# ReAct 编排能力开发技术文档

## 一、概述

本文档描述 AI Foundation 平台 ReAct（Reasoning + Acting）编排能力的设计与实现。ReAct 是一种 Agent 运行模式，大模型通过"思考-调用工具-观察结果-继续思考"的多轮循环，自主完成复杂任务。

### 核心能力

- 基于 Spring AI Alibaba Agent Framework 的 `ReactAgent` 实现图状态机编排
- 支持 CLI 命令作为工具注册与调用（API 型）
- 支持流式输出（思考过程、正文 Token、工具调用、工具结果）
- 与项目绑定的能力挂载机制

### 技术栈

| 组件 | 版本 | 作用 |
|---|---|---|
| Spring AI Alibaba Agent Framework | 1.1.2.0 | ReAct 编排核心（ReactAgent） |
| Spring AI | 1.1.2 | 模型抽象 + ToolCallback 体系 |
| WebFlux / Reactor | - | 响应式流式处理 |

---

## 二、整体架构

### 分层架构

```
┌─────────────────────────────────────────────┐
│              Gateway 接入层                  │
│  RunController / ChatController (SSE)       │
├─────────────────────────────────────────────┤
│              Mediator 编排层                 │
│  ┌───────────────────────────────────────┐  │
│  │        AgentOrchestrator              │  │
│  │  (调度: chat / react 分支)            │  │
│  └──────────────┬────────────────────────┘  │
│                 │                            │
│  ┌──────────────▼────────────────────────┐  │
│  │     ReactAgentRunner (ReAct 核心)     │  │
│  │  - 构建 ToolCallback 列表              │  │
│  │  - 构建系统提示词                      │  │
│  │  - 启动 ReactAgent 流式执行            │  │
│  └──────────────┬────────────────────────┘  │
│                 │                            │
│  ┌──────────────▼────────────────────────┐  │
│  │     ReactCliToolFactory               │  │
│  │  (CLI → ToolCallback 工厂)            │  │
│  └──────────────┬────────────────────────┘  │
│                 │                            │
│  ┌──────────────▼────────────────────────┐  │
│  │     ReactCliToolInvoker               │  │
│  │  (工具调用执行入口)                    │  │
│  └──────────────┬────────────────────────┘  │
│                 │                            │
│  ┌──────────────▼────────────────────────┐  │
│  │     CliCommandExecutor                │  │
│  │  + CliParamBinder (参数绑定)          │  │
│  │  + ApiToolExecutor (HTTP 调用)        │  │
│  └───────────────────────────────────────┘  │
├─────────────────────────────────────────────┤
│              Biz / DAL 数据层                │
│  CLI命令 / 参数 / 工具定义 / API Schema      │
└─────────────────────────────────────────────┘
```

### 核心流程图

```
用户请求 → 创建Run(runType=react) → 订阅SSE事件流
                                     │
                                     ▼
                          ┌───────────────────┐
                          │  start events     │
                          │  (run_start,      │
                          │   user_message)   │
                          └─────────┬─────────┘
                                    │
                                    ▼
                          ┌───────────────────┐
                          │  ReactAgent       │
                          │  .stream()        │
                          └─────────┬─────────┘
                                    │
             ┌──────────────────────┼──────────────────────┐
             ▼                      ▼                      ▼
     ┌──────────────┐      ┌────────────────┐      ┌───────────────┐
     │ 思考链输出   │      │ 正文Token输出   │      │ 工具调用事件  │
     │ (reasoning)  │      │ (chat_token)   │      │ (tool_call)   │
     └──────────────┘      └────────────────┘      └───────┬───────┘
                                                           │
                                                           ▼
                                                  ┌─────────────────┐
                                                  │ 执行CLI命令     │
                                                  │ (HTTP API调用)  │
                                                  └─────────┬───────┘
                                                            │
                                                            ▼
                                                  ┌─────────────────┐
                                                  │ 工具结果事件    │
                                                  │ (tool_result)   │
                                                  └─────────┬───────┘
                                                            │
                                                            ▼
                                                  (回到模型继续思考)
                                                            │
                                                            ▼
                                                  ┌─────────────────┐
                                                  │ 最终回复        │
                                                  │ (run_complete)  │
                                                  └─────────────────┘
```

---

## 三、核心模块详解

### 3.1 ReactAgentRunner — ReAct 编排入口

**文件**: `mediator/agent/react/core/ReactAgentRunner.java`

ReAct 编排的主入口，负责：

1. **构建运行会话** (`ReactRunSession`)：存储本次 Run 的上下文（runCode、用户消息、项目ID、模型名等）
2. **加载项目 CLI 工具**：通过 `AgentProjectCliMappingService` 获取项目绑定的 CLI 列表
3. **构建 ToolCallback**：调用 `ReactCliToolFactory` 将 CLI 转为 Spring AI 的 `ToolCallback`
4. **构建系统提示词**：默认 ReAct 指令 + 项目系统提示词 + 可用工具概览
5. **启动 ReactAgent**：使用 `ReactAgent.builder()` 构建 Agent，调用 `stream()` 方法启动流式执行
6. **事件转换**：通过 `ReactStreamHandler` 将 `NodeOutput` 转为 `RunStreamEnvelope`

```java
// 核心方法签名
public Flux<RunStreamEnvelope> streamReactRun(
    AgentRun run,                // Run 实体
    AgentConversationInfo conv,  // 会话实体
    String userMessage,          // 用户消息
    String systemPrompt,         // 可选系统提示词
    Sinks.Empty<Void> cancelSig  // 取消信号
)
```

#### ReactAgent 构建

```java
ReactAgent agent = ReactAgent.builder()
    .name("foundation_react_agent")
    .model(chatModel)              // ChatModel bean
    .chatOptions(chatOptions)      // 模型参数
    .tools(tools)                  // ToolCallback 列表
    .systemPrompt(finalSystemPrompt)
    .build();
```

### 3.2 ReactRunSession — 运行时上下文

**文件**: `mediator/agent/react/core/ReactRunSession.java`

使用 `ThreadLocal` 存储当前 ReAct Run 的上下文，解决工具回调中无法直接传参的问题。

```java
public class ReactRunSession {
    private static final ThreadLocal<ReactRunSession> HOLDER = new ThreadLocal<>();

    private String runCode;
    private String conversationCode;
    private String userMessage;
    private String accessToken;
    private String modelName;
    private Long projectId;
    private AgentExecutionContext executionContext;
    private List<AgentCliCommand> availableCliCommands;
    private List<String> toolInterpretedResults;

    // ThreadLocal 操作
    public static ReactRunSession current();
    public static void set(ReactRunSession session);
    public static void clear();

    // 带会话执行（确保工具回调在正确的上下文中运行）
    public static <T> T callWithSession(ReactRunSession session, Callable<T> callable);
}
```

> **为什么需要 ThreadLocal？** Spring AI 的 `FunctionToolCallback` 是函数式接口，工具调用由框架内部线程触发，无法通过方法参数传递 Run 上下文。通过 ThreadLocal + `callWithSession` 保证工具执行时能获取到当前 Run 的信息。

### 3.3 ReactStreamHandler — 流式事件处理器

**文件**: `mediator/agent/react/core/ReactStreamHandler.java`

将 Spring AI Alibaba 的 `NodeOutput`（实际是 `StreamingOutput`）转换为平台统一的 `RunStreamEnvelope` SSE 事件。

#### 事件类型映射

| StreamingOutput 类型 | 平台事件类型 | 说明 |
|---|---|---|
| `AGENT_MODEL_STREAMING` | `CHAT_REASONING` / `CHAT_TOKEN` | 模型流式输出，首次输出前是思考，工具调用后是正文 |
| `AGENT_MODEL_FINISHED` | `TOOL_CALL` | 模型决定调用工具，解析 toolCalls |
| `AGENT_TOOL_FINISHED` | `TOOL_RESULT` | 工具执行完成，返回结果 |

#### 思考 vs 正文的判断

- 第一轮工具调用前的输出 → 思考链（`CHAT_REASONING`）
- 工具调用后的输出 → 正文（`CHAT_TOKEN`）
- 这是简化策略，老项目有更复杂的 `shouldStreamAnswer` 判断

### 3.4 ReactCliToolFactory — CLI 工具工厂

**文件**: `mediator/agent/react/cli/ReactCliToolFactory.java`

将数据库中的 CLI 命令定义转换为 Spring AI 的 `ToolCallback`，让大模型可以调用。

每个 CLI 命令生成一个 `FunctionToolCallback`，包含：

| 属性 | 来源 | 说明 |
|---|---|---|
| Tool Name | `react_cli_` + `commandName` | 工具唯一标识，模型通过此名称调用 |
| Description | CLI描述 + 参数说明 + 调用提示 | 告诉模型这个工具有什么用、怎么用 |
| Input Schema | 由 `AgentCliParam` 构建 JSON Schema | 定义工具入参的结构 |
| Function | `ReactCliToolInvoker::invoke` | 实际执行逻辑 |

```java
// 生成的工具名示例
// CLI commandName = "epms_cli_api_order_list"
// → Tool name = "react_cli_epms_cli_api_order_list"
```

### 3.5 ReactCliToolSchemaBuilder — 参数 Schema 构建器

**文件**: `mediator/agent/react/cli/ReactCliToolSchemaBuilder.java`

根据 `AgentCliParam` 列表构建 JSON Schema，作为 Tool 的输入定义。

```json
{
  "type": "object",
  "properties": {
    "params": {
      "type": "object",
      "description": "CLI 命令参数键值对",
      "properties": {
        "hotelCode": {
          "type": "string",
          "description": "酒店编码"
        },
        "pageNum": {
          "type": "number",
          "description": "页码"
        }
      },
      "required": ["hotelCode"],
      "additionalProperties": true
    }
  },
  "required": ["params"]
}
```

> **为什么包一层 `params`？** 统一入参结构，所有 CLI 工具的参数都放在 `params` 对象里，避免与 Spring AI 的工具调用机制冲突。

### 3.6 ReactCliToolInvoker — 工具调用器

**文件**: `mediator/agent/react/cli/ReactCliToolInvoker.java`

工具回调的实际执行入口，负责：

1. 从 `ReactRunSession` 获取当前上下文
2. 构建 `CliParamBindContext`（参数绑定上下文）
3. 调用 `CliCommandExecutor.executeApi()` 执行 API 调用
4. 记录工具调用计数，缓存执行结果
5. 返回工具执行结果文本（供模型继续思考）

```java
public String invoke(AgentCliCommand cli, ReactCliToolInput input) {
    ReactRunSession session = ReactRunSession.current();
    // ... 构建上下文 ...
    String result = cliCommandExecutor.executeApi(
        cli.getId(), bindContext,
        session.getAccessToken(),
        session.getExecutionContext(),
        session.getModelName()
    );
    return result;
}
```

### 3.7 CliCommandExecutor — CLI 命令执行器

**文件**: `mediator/agent/executor/CliCommandExecutor.java`

CLI 命令的统一执行器（当前仅支持 API 类型，可扩展 PAGE 类型）。

执行流程：
1. 校验 CLI 存在且类型匹配
2. 加载 Tool 定义（URL、Method 等）
3. 加载参数定义
4. 通过 `CliParamBinder` 绑定参数
5. 调用 `ApiToolExecutor` 执行 HTTP 请求
6. 返回响应结果

### 3.8 CliParamBinder — 参数绑定器

**文件**: `mediator/agent/executor/CliParamBinder.java`

简化版参数绑定，策略如下：

1. 优先使用工具调用传入的参数（`prefilledParams`）
2. 其次使用参数定义中的默认值（`defaultValue`）
3. 必填参数缺失时打 WARN 日志（不抛出异常，交由执行层处理）

> **后续可扩展**：支持 LLM 智能提取参数（从用户自然语言中提取参数值），老项目有 `LlmCliParamExtractor`。

### 3.9 ApiToolExecutor — API 工具执行器

**文件**: `mediator/agent/executor/ApiToolExecutor.java`

实际发起 HTTP 请求的组件，基于 `WebClient`。

特性：
- 支持 GET / POST 方法
- 支持 `schemaCode` 解析 BaseUrl（从 `agent_api_schema_config` 表）
- 自动注入 `x-access-titc-c-token`（accessToken）和 `x-hotel-code` 头
- 30 秒超时
- 参数清洗（过滤 null / 空字符串）

```java
public String execute(AgentToolDefinition tool, Map<String, Object> params, String accessToken)
```

URL 构建规则：
- Tool URL 以 `http` 开头 → 直接使用完整 URL
- 否则 → `schemaBaseUrl + toolUrl` 拼接

---

## 四、与现有架构的集成

### 4.1 Run 类型扩展

`AgentRun` 的 `runType` 字段新增 `react` 类型（已在 `RunTypeConstant` 中定义）。

| runType | 编排器 | 说明 |
|---|---|---|
| `chat` | AgentOrchestrator | 单轮对话，无工具调用 |
| `react` | ReactAgentRunner | ReAct 模式，支持工具调用 |

### 4.2 AgentRunMedService 改动

`createRun` 方法增加 `runType` 参数：

```java
public String createRun(String conversationCode, String userMessage,
                        String systemPrompt, String runType, String clientIp)
```

`streamRunEvents` 根据 runType 分支：

```java
if (RunTypeConstant.REACT.equals(pending.runType())) {
    mainStream = reactAgentRunner.streamReactRun(...);
} else {
    mainStream = orchestrator.streamRun(...);
}
```

### 4.3 前端调用方式

```typescript
// 创建 ReAct 模式的 Run
const { runCode } = await createRun({
  conversationCode: 'conv_xxx',
  userMessage: '帮我查一下北京王府井店的订单列表',
  runType: 'react'  // 新增字段
});

// 订阅 SSE 事件流（和 chat 模式一样）
const eventSource = new EventSource(`/chat/runs/events?runCode=${runCode}`);
```

### 4.4 SSE 事件列表（ReAct 模式）

| 事件类型 | data 内容 | 说明 |
|---|---|---|
| `run_start` | null | Run 开始 |
| `chat_start` | null | 对话开始 |
| `user_message` | string | 用户消息 |
| `chat_reasoning` | string | 思考链内容（流式） |
| `chat_token` | string | 正文内容（流式） |
| `tool_call` | `{name, arguments}` | 模型决定调用工具 |
| `tool_result` | `{toolName, result}` | 工具执行结果 |
| `chat_complete` | null | 对话完成 |
| `run_complete` | string（最终回复） | Run 完成 |
| `run_error` | string（错误信息） | Run 失败 |

---

## 五、新增文件清单

### backend 端

| 文件路径 | 说明 |
|---|---|
| `ai-foundation-com/.../constant/CliCommandTypeConstant.java` | CLI 命令类型常量 |
| `ai-foundation-mediator/.../agent/context/AgentExecutionContext.java` | 执行上下文 |
| `ai-foundation-mediator/.../agent/executor/ApiToolExecutor.java` | API 工具执行器 |
| `ai-foundation-mediator/.../agent/executor/CliCommandExecutor.java` | CLI 命令执行器 |
| `ai-foundation-mediator/.../agent/executor/CliParamBinder.java` | 参数绑定器 |
| `ai-foundation-mediator/.../agent/executor/CliParamBindContext.java` | 参数绑定上下文 |
| `ai-foundation-mediator/.../agent/react/core/ReactAgentRunner.java` | ReAct 编排器（核心） |
| `ai-foundation-mediator/.../agent/react/core/ReactRunSession.java` | ReAct 会话上下文 |
| `ai-foundation-mediator/.../agent/react/core/ReactStreamHandler.java` | 流式事件处理器 |
| `ai-foundation-mediator/.../agent/react/cli/ReactCliToolFactory.java` | CLI 工具工厂 |
| `ai-foundation-mediator/.../agent/react/cli/ReactCliToolInvoker.java` | CLI 工具调用器 |
| `ai-foundation-mediator/.../agent/react/cli/ReactCliToolNames.java` | 工具名工具类 |
| `ai-foundation-mediator/.../agent/react/cli/ReactCliToolSchemaBuilder.java` | 参数 Schema 构建器 |
| `ai-foundation-mediator/.../agent/react/dto/ReactCliToolInput.java` | 工具输入 DTO |

### 修改的文件

| 文件路径 | 修改内容 |
|---|---|
| `RunController.java` | createRun 透传 runType |
| `AgentRunMedService.java` | createRun 增加 runType 参数，streamRunEvents 分支处理 |
| `CreateRunRequest.java` | 新增 runType 字段 |
| `AgentApiSchemaConfigService.java` | 新增 getBySchemaCode 方法 |
| `ai-foundation-mediator/pom.xml` | 替换 graph-core 为 agent-framework 依赖 |

---

## 六、配置与依赖

### Maven 依赖

```xml
<!-- Spring AI Alibaba Agent Framework (含 ReactAgent) -->
<dependency>
    <groupId>com.alibaba.cloud.ai</groupId>
    <artifactId>spring-ai-alibaba-agent-framework</artifactId>
</dependency>
```

> 该依赖已包含 `spring-ai-alibaba-graph-core`，无需单独引入。

### 数据库表依赖

ReAct 运行时依赖以下表（CLI 管理模块已建）：

- `agent_cli_command` — CLI 命令定义
- `agent_cli_param` — CLI 参数定义
- `agent_tool_definition` — API 工具定义（URL、Method、Schema 等）
- `agent_api_schema_config` — API 网关配置（BaseUrl）
- `agent_project_cli_rel` — 项目与 CLI 的绑定关系

---

## 七、后续扩展方向

### 高优先级

1. **CLI 参数智能提取**：当模型传参不全时，通过 LLM 从用户自然语言中提取缺失参数（老项目有 `LlmCliParamExtractor`）
2. **API 结果解读**：将 API 返回的 JSON 通过 LLM 解读为自然语言（老项目有 `ApiToolResultInterpreter`）
3. **业务成功判断**：根据 `successCheckJson` 判断 API 返回是否业务成功（老项目有 `ApiBizSuccessChecker`）
4. **PAGE 类型 CLI 支持**：支持页面跳转型命令（`NavigateExecutor`）

### 中优先级

5. **多轮对话历史**：ReAct 模式支持多轮对话（当前每次 Run 都是独立的）
6. **Checkpoint 持久化**：对话状态保存到 Redis，支持中断恢复
7. **工具调用重试**：工具失败时自动重试
8. **Token 用量统计**：统计每轮 ReAct 的 prompt / completion tokens

### 低优先级

9. **Skill 工具类型**：扩展支持 Skill 类型的工具
10. **知识库工具类型**：扩展支持知识库检索
11. **Excel 导出 / 文件上传**：平台级工具
12. **向量召回能力**：项目 CLI 很多时，通过向量检索筛选本轮可用工具

---

## 八、设计要点

### 为什么用 Spring AI Alibaba 的 ReactAgent 而不是自己实现？

- ReactAgent 基于图状态机（StateGraph），天然支持 checkpoint / 中断恢复 / hook / interceptor 等高级特性
- ToolCallback 体系与 Spring AI 生态无缝集成，OpenAI / 通义 / 豆包等模型都支持
- 减少重复造轮子，聚焦业务逻辑

### 为什么工具名要加 `react_cli_` 前缀？

- 区分不同类型的工具（CLI / Skill / Knowledge / Platform）
- 模型看到工具名就能知道是什么类型的能力
- 避免命名冲突

### 为什么用 ThreadLocal 传上下文？

Spring AI 的 `FunctionToolCallback` 是无状态的函数接口，工具被调用时无法获取是哪个 Run 触发的。通过 ThreadLocal + `callWithSession` 确保工具执行线程有正确的上下文。

> ⚠️ 注意：必须保证工具回调在同一个线程执行，或者通过 `callWithSession` 显式绑定。

---

## 九、验证方式

### 前置条件

1. 数据库中有 `agent_cli_command` + `agent_cli_param` + `agent_tool_definition` + `agent_api_schema_config` 数据
2. 项目已通过"挂载能力"绑定了 CLI
3. 模型配置正常（能正常对话）

### 测试步骤

```bash
# 1. 创建 ReAct Run
curl -X POST http://localhost:8080/chat/runs/create \
  -H "Content-Type: application/json" \
  -d '{
    "conversationCode": "test_conv_001",
    "userMessage": "帮我查一下酒店HT001的订单列表",
    "runType": "react"
  }'

# 2. 订阅事件流
curl -N "http://localhost:8080/chat/runs/events?runCode=xxx"

# 期望看到的事件顺序:
# run_start → chat_start → user_message → chat_reasoning(思考)
# → tool_call(调用订单查询工具) → tool_result(返回结果)
# → chat_token(最终回答) → chat_complete → run_complete
```
