# ReAct 取消中断机制 · Spring AI Alibaba Hook 落地

> 配套：[react-orchestration-design.md](./react-orchestration-design.md) · [react-capability-backlog.md](./react-capability-backlog.md) 中的「步骤 2」
>
> 状态：已实现 · 分支 `codex/react-cancel-hook`

## 一、问题

老项目靠 `AgentRunMedService` 里的 `Sinks.Empty<Void>` 做取消，只能掐断最外层 `Flux`，**Spring AI Alibaba 的 ReAct 图内部循环停不下来**：

- 用户点「取消」 → `signal.tryEmitEmpty()` 触发外层 `Flux.merge(...)` 终止
- 但 `agent.stream(messages, runnableConfig)` 返回的 `Flux<NodeOutput>` 还在继续发射
- 模型已经在生成下一段 token / 已经在调下一个工具，**只是没推到前端 SSE**
- 资源（token、工具调用）继续被消耗

更严重的是：当前 `Sinks` 是**单进程内存**信号，多副本部署下，负载均衡把 `/chat/runs/cancel` 请求路由到非 Run 所在实例时，取消就丢了。

## 二、方案概览

借助 Spring AI Alibaba 框架本身留的钩子把取消下沉到「图执行层」：

```
┌──────────────────────────────────────────────────────────────────┐
│  /chat/runs/cancel                                                │
│      │                                                            │
│      ▼                                                            │
│  AgentRunMedService.cancelRun                                     │
│      ├─ Sinks.Empty.tryEmitEmpty()        ← 老路径：掐外层 Flux  │
│      └─ RunCancelFlagStore.markCancelled  ← 新路径：写 Redis 标志  │
│                              │                                     │
│                              ▼ (任意副本可读)                       │
│              ai:foundation:run:cancel:<runCode>  =  "1"            │
│                              │                                     │
│                              ▼                                     │
│  ReactAgent.stream() 每轮 LLM 调用前                              │
│      │                                                            │
│      ▼                                                            │
│  ReactCancelModelHook.interrupt(nodeId, state, config)            │
│      │                                                            │
│      ├─ Optional.empty()                          → 继续           │
│      └─ Optional.of(InterruptionMetadata)         → 框架停图        │
└──────────────────────────────────────────────────────────────────┘
```

## 三、Spring AI Alibaba 留的接入点

实现这个机制需要用到框架里**两个相邻的接口**：

### 3.1 `ModelHook` —— 模型调用生命周期

```java
// com.alibaba.cloud.ai.graph.agent.hook.ModelHook
public abstract class ModelHook {
    public abstract String getName();

    public CompletableFuture<Map<String, Object>> beforeModel(OverAllState state, RunnableConfig config);
    public CompletableFuture<Map<String, Object>> afterModel(OverAllState state, RunnableConfig config);
}
```

返回的 `Map` 会被框架 merge 进 `OverAllState`。光靠 `beforeModel` 也能「绕着弯」实现中断（比如往 state 写个 `__cancelled__` key，让上游节点读），但**不是显式中断**，不优雅。

### 3.2 `InterruptableAction` —— 真正的中断口子

```java
// com.alibaba.cloud.ai.graph.action.InterruptableAction
public interface InterruptableAction {
    Optional<InterruptionMetadata> interrupt(String nodeId, OverAllState state, RunnableConfig config);
}
```

- `Optional.empty()` → 当前节点继续
- `Optional.of(metadata)` → 框架**立即停图**，后续节点不再调度

`InterruptionMetadata` 用 builder 模式构造，可以塞任意 metadata：

```java
InterruptionMetadata.builder(nodeId, state)
    .addMetadata("cancelled_by_user", Boolean.TRUE)
    .addMetadata("run_code", runCode)
    .build();
```

### 3.3 框架调用时机（推断 + 日志佐证）

```
ReAct 图每轮 LLM 调用
      ↓
1. 框架遍历所有 hooks，调 beforeModel()
      ↓
2. 框架筛选 implements InterruptableAction 的 hook
      ↓
3. 调 interrupt(nodeId, state, config)
      ↓
4a. 全部返回 Optional.empty()  → 继续调 LLM
4b. 任一返回非空             → 停图，stream 序列自然结束
```

老项目日志里 `interrupt fired, runCode=..., nodeId=...` 出现后 `agent.stream(...)` 立刻不再有 `NodeOutput` 出来，证明 `Optional.of(metadata)` 这条路径**真的会让图停转**。

## 四、本次实现

### 4.1 文件清单

| 类型 | 路径 | 说明 |
| --- | --- | --- |
| 新建 | `backend/ai-foundation-mediator/src/main/java/com/ai/foundation/mediator/agent/event/RunCancelFlagStore.java` | Redis 协作标记（SET / EXISTS / DEL） |
| 新建 | `backend/ai-foundation-mediator/src/main/java/com/ai/foundation/mediator/agent/react/core/ReactCancelModelHook.java` | 框架 `ModelHook` + `InterruptableAction` 实现 |
| 改 | `backend/ai-foundation-com/src/main/java/com/ai/foundation/com/constant/RedisKeyConstants.java` | 新增 `RUN_CANCEL_FLAG` key + `runCancelFlag(runCode)` 构造器 |
| 改 | `backend/ai-foundation-mediator/src/main/java/com/ai/foundation/mediator/agent/react/core/ReactAgentRunner.java` | 注入 `RunCancelFlagStore`；`ReactAgent.builder().hooks(...)` 挂上 |
| 改 | `backend/ai-foundation-mediator/src/main/java/com/ai/foundation/mediator/run/AgentRunMedService.java` | `cancelRun` 同步写 Redis；`streamRunEvents.doFinally` 清理 |

### 4.2 `RunCancelFlagStore`（Redis 协作标记）

```java
@Component
public class RunCancelFlagStore {
    static final long DEFAULT_TTL_SECONDS = 3600L;
    private final StringRedisTemplate stringRedisTemplate;

    public void markCancelled(String runCode) {
        stringRedisTemplate.opsForValue().set(
            RedisKeyConstants.runCancelFlag(runCode.trim()),
            "1", DEFAULT_TTL_SECONDS, TimeUnit.SECONDS);
    }

    public boolean isCancelled(String runCode) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(
            RedisKeyConstants.runCancelFlag(runCode.trim())));
    }

    public void clear(String runCode) {
        stringRedisTemplate.delete(RedisKeyConstants.runCancelFlag(runCode.trim()));
    }
}
```

**关键设计**：

- **Redis 而不是内存**：跨实例立即生效。`/chat/runs/cancel` 落到 gateway-A，Run 在 gateway-B 上跑，gateway-A 写 Redis 一次，gateway-B 下一轮 LLM 之前的 `interrupt()` 就能看到。
- **TTL = 1 小时**：防漏删。Run 正常应在分钟级内结束，残留标志 1 小时后自动过期。
- **异常吞掉不抛**：`markCancelled` / `isCancelled` / `clear` 各自 try-catch。`isCancelled` 在 Redis 异常时返回 `false`（不取消）—— **宁可不中断，也不要因为标记丢失而误中断正常 Run**。
- **key 不含敏感信息**：`ai:foundation:run:cancel:<runCode>`，只放字符串 `"1"`。

### 4.3 `ReactCancelModelHook`（框架钩子）

```java
public class ReactCancelModelHook extends ModelHook implements InterruptableAction {
    private static final String HOOK_NODE_NAME = "react_cancel_hook";
    private final RunCancelFlagStore runCancelFlagStore;
    private final String runCode;

    @Override
    public String getName() {
        return HOOK_NODE_NAME;
    }

    @Override
    public CompletableFuture<Map<String, Object>> beforeModel(OverAllState state, RunnableConfig config) {
        // beforeModel 不改 state；实际终止由 interrupt() 处理
        return CompletableFuture.completedFuture(Collections.emptyMap());
    }

    @Override
    public Optional<InterruptionMetadata> interrupt(String nodeId, OverAllState state, RunnableConfig config) {
        if (StringUtils.isBlank(runCode) || runCancelFlagStore == null) {
            return Optional.empty();
        }
        if (!runCancelFlagStore.isCancelled(runCode)) {
            return Optional.empty();
        }
        log.info("ReactCancelModelHook interrupt fired, runCode={}, nodeId={}", runCode, nodeId);
        return Optional.of(InterruptionMetadata.builder(nodeId, state)
                .addMetadata("cancelled_by_user", Boolean.TRUE)
                .addMetadata("run_code", runCode)
                .build());
    }
}
```

**关键设计**：

- **同时 `extends ModelHook` + `implements InterruptableAction`**：框架通过接口扫描发现 `InterruptableAction` 能力，从而在每轮 LLM 调用前回调 `interrupt()`。
- **`beforeModel` 故意空实现**：所有取消判定集中在 `interrupt()` 一处，避免分散；返回 `Collections.emptyMap()` 是「不改 state」的明示。
- **`interrupt` 三个分支**：
  - runCode 空白 / store 为 null → `empty()` 兜底
  - Redis 未标记 → `empty()` 继续
  - Redis 已标记 → `Optional.of(metadata)` 停图
- **元数据可观测**：`cancelled_by_user` 与 `run_code` 写进 `OverAllState`，便于上层 `agent.stream()` 订阅者 / `stepState` 写入时识别中断来源。
- **不复用标志**：方法体内不调 `clear()`，留给 Run 终态时由 `AgentRunMedService.streamRunEvents.doFinally` 统一清理，避免并发 Run 误判。

### 4.4 接入 `ReactAgent.builder()`

```java
ReactAgent agent = ReactAgent.builder()
        .name("foundation_react_agent")
        .model(chatModel)
        .chatOptions(chatOptions)
        .tools(tools)
        .systemPrompt(finalSystemPrompt)
        .hooks(new ReactCancelModelHook(runCancelFlagStore, runCode))   // ← 新增
        .build();
```

`hooks(...)` 接 `ModelHook...` 可变参数，**与未来要加的 `ReactUsageHook` / `ReactTracingHook` 并列**。

### 4.5 `AgentRunMedService` 接入点

```java
public void cancelRun(String runCode, String operator) {
    // ... 前置校验不变 ...
    Sinks.Empty<Void> signal = cancelSignals.get(trimmedRunCode);
    if (signal != null) {
        signal.tryEmitEmpty();                    // 老路径：掐外层 Flux
    }
    // 新增：同步写 Redis 协作标志，让框架层在下一轮 LLM 调用前真正停图
    runCancelFlagStore.markCancelled(trimmedRunCode);
}
```

```java
.doFinally(signal -> {
    cancelSignals.remove(trimmedRunCode);
    runCancelFlagStore.clear(trimmedRunCode);     // 新增：终态清理
});
```

## 五、取消时序

```
T+0ms    用户点「取消」
         └─→ POST /chat/runs/cancel  (gateway-A 收到请求)
T+5ms    AgentRunMedService.cancelRun
         ├─ Sinks.Empty.tryEmitEmpty()            // 切外层 Flux（前端 SSE 立刻收尾）
         └─ RunCancelFlagStore.markCancelled      // 写 Redis
                 └─→ ai:foundation:run:cancel:<runCode> = "1" (TTL 1h)
T+200ms  gateway-B 上的 ReactAgent.stream()
         进入下一轮 LLM 调用前
         └─→ 框架扫描到 ReactCancelModelHook implements InterruptableAction
              └─→ interrupt(nodeId, state, config)
                   └─→ runCancelFlagStore.isCancelled → true
                        └─→ 返回 InterruptionMetadata
                             └─→ 框架停图，stream 序列结束
T+205ms  AgentRunMedService.streamRunEvents.doFinally
         └─→ runCancelFlagStore.clear(runCode)    // 清理标志
```

## 六、与原 `Sinks.Empty<Void>` 路径的关系

| 维度 | `Sinks.Empty<Void>`（老） | `RunCancelFlagStore`（新） |
| --- | --- | --- |
| 作用层 | 外层 `Flux`（SSE 输出） | 框架 `ReactAgent` 图循环 |
| 生效范围 | 单进程内存 | 跨实例（Redis） |
| 模型已发的 token | 不会回收 | **真正停算** |
| 工具调用 | 外层切了但框架内继续 | 框架停图后不再调度 |
| 失败时表现 | 取消信号丢 | 标志丢 → 兜底为不取消（安全） |

两条路径**双保险**：
- 老 `Sinks` 保证前端 SSE 立刻断流、UI 看到 `RUN_CANCELLED`；
- 新 `RunCancelFlagStore` 保证后端图循环真正停转、不再消耗资源。

后续若确认框架层中断足够稳，可以删除 `Sinks` 路径简化代码；当前保留是出于风险隔离。

## 七、验证

### 7.1 编译

```bash
cd backend
mvn -pl ai-foundation-mediator -am -Pqa -DskipTests compile
```

通过（仅有框架 / JVM 警告，与本次实现无关）。

### 7.2 单元测试（待补）

最小测试集建议：

1. `RunCancelFlagStoreTest`：
   - `markCancelled` → `isCancelled` 返回 `true`
   - `clear` → `isCancelled` 返回 `false`
   - `markCancelled` 时 Redis 抛异常 → 方法不抛
   - `isCancelled` 时 Redis 抛异常 → 返回 `false`（不取消）
2. `ReactCancelModelHookTest`：
   - 未标记时 `interrupt` 返回 `Optional.empty()`
   - 已标记时返回带 `cancelled_by_user=true` 元数据的 `InterruptionMetadata`
   - runCode 为 null / 空时返回 `empty()`

### 7.3 联调（待补）

在 Playground 走通一次完整 Run：

1. 发一个会触发多轮 ToolCall 的 ReAct Run（如「查订单 → 查详情 → 跳转」）
2. 在第二轮 LLM 调用中点「取消」
3. 观察：
   - 前端 SSE 立刻收到 `RUN_CANCELLED`
   - 后端日志出现 `ReactCancelModelHook interrupt fired, runCode=..., nodeId=...`
   - `agent_run.task_state = CANCELLED`
   - 后续 ToolCall 不再发生（agent_run_task_info 不再写入新行）
   - Redis 中 `ai:foundation:run:cancel:<runCode>` 在 SSE 断开后被 `clear`

## 八、未做事项

- **Plan 模式的取消**：当前 Plan 模式的「确认 / 拒绝」在 `AgentRunMedService.confirmRun` 走另一条路径，本次的取消 hook 不覆盖它。
- **跨步取消的元数据透传**：`InterruptionMetadata` 里的 `cancelled_by_user` / `run_code` 当前只写进 `OverAllState`，还没有传回 `agent_run_info` 字段，待后续 Plan 模式落地时一并接。
- **多实例全局 Sinks 取消**：本机制把取消信号搬到 Redis 之后，本进程内的 `cancelSignals` 仍有意义（用于快速掐 SSE），不需要替换。

## 九、相关文件

- 老项目参考实现：
  - [`ReactCancelModelHook.java`](/Users/luyan/workspace/tcworkspace5/ai-foundation/ai-foundation-mediator/src/main/java/com/ly/titc/ai/foundation/mediator/agent/react/core/ReactCancelModelHook.java)
  - [`RunCancelFlagStore.java`](/Users/luyan/workspace/tcworkspace5/ai-foundation/ai-foundation-mediator/src/main/java/com/ly/titc/ai/foundation/mediator/agent/event/RunCancelFlagStore.java)
- 新项目实现：
  - `backend/ai-foundation-mediator/src/main/java/com/ai/foundation/mediator/agent/event/RunCancelFlagStore.java`
  - `backend/ai-foundation-mediator/src/main/java/com/ai/foundation/mediator/agent/react/core/ReactCancelModelHook.java`
  - `backend/ai-foundation-com/src/main/java/com/ai/foundation/com/constant/RedisKeyConstants.java`
  - `backend/ai-foundation-mediator/src/main/java/com/ai/foundation/mediator/agent/react/core/ReactAgentRunner.java`
  - `backend/ai-foundation-mediator/src/main/java/com/ai/foundation/mediator/run/AgentRunMedService.java`
