# CLI 执行链路详解：从 LLM 决策到 HTTP 调用

> 本文梳理 ai_foundation 项目中「大模型如何自主决定调用某个 CLI，以及这个调用如何最终变成一次真实的 HTTP / MCP 请求」的完整代码链路。
>
> 核心结论：**LLM 从不直接发 HTTP**。它只输出一个结构化的 `tool_calls` JSON（"我想调哪个工具、传什么参数"），真正把这段 JSON 变成 HTTP 请求的是 **Spring AI 的 function-calling 回调机制 + 项目里注册的 `FunctionToolCallback` lambda**。模型负责"决策"，后端负责"执行"，两者靠 tool 的 `name` 和 `inputSchema` 这套契约对接。

---

## 一、涉及的核心类

所有执行相关代码都在 `ai-foundation-mediator` 模块下，分三层：

| 层 | 类 | 职责 |
|---|---|---|
| 编排层 | `ReactAgentRunner` | 加载项目 CLI、包装成 Tool、构建 ReactAgent、驱动 ReAct 流式循环 |
| 调度层 | `ReactCliToolFactory` / `ReactCliToolInvoker` / `CliCommandExecutor` | 把 CLI 包成 `ToolCallback`；被模型回调后建任务、超时重试、按类型分发 |
| 执行层 | `ApiToolExecutor` / `McpToolExecutor` | **真正发请求**：API 走 WebClient HTTP，MCP 走 stdio 子进程 |

辅助类：

| 类 | 职责 |
|---|---|
| `ReactRunSession` | 用 ThreadLocal 在一次 Run 内传递 runId / userMessage / accessToken 等上下文 |
| `ReactCliToolNames` | 工具命名：`react_cli_<commandName>` |
| `ReactCliToolSchemaBuilder` | 由 `AgentCliParam` 列表生成 tool 的 inputSchema（JSON Schema） |
| `CliParamBinder` / `CliParamBindContext` | 把模型传入的参数与 DB 中定义的默认值/上下文合并成最终请求参数 |

对应文件路径（均在 `backend/ai-foundation-mediator/src/main/java/com/ai/foundation/mediator/` 下）：

```
agent/react/core/ReactAgentRunner.java
agent/react/core/ReactRunSession.java
agent/react/cli/ReactCliToolFactory.java
agent/react/cli/ReactCliToolInvoker.java
agent/react/cli/ReactCliToolNames.java
agent/react/cli/ReactCliToolSchemaBuilder.java
agent/executor/CliCommandExecutor.java
agent/executor/ApiToolExecutor.java
agent/executor/McpToolExecutor.java
```

---

## 二、完整触发链（以一次 API 类型 CLI 为例）

### 第 0 步：把 CLI 注册成模型看得懂的 tool

入口 `ReactAgentRunner.streamReactRun`（`ReactAgentRunner.java:76`）：

1. `loadProjectCliCommands(projectId)`（`:229`）从 DB 查出当前 project 挂载的所有 `state=1` 且 `commandType ∈ {API, MCP}` 的 CLI（含通过 Skill 关联进来的）。
2. `ReactCliToolFactory.buildCliTools`（`ReactCliToolFactory.java:29`）把每条 CLI 包装成一个 Spring AI 的 `FunctionToolCallback`，关键三样东西（`:53-59`）：
   - **toolName** = `react_cli_<commandName>`（如 `react_cli_gaode_geocode`），由 `ReactCliToolNames.resolveToolName` 生成。
   - **inputSchema** = 由 `AgentCliParam` 列表生成的 JSON Schema（`ReactCliToolSchemaBuilder.buildInputSchema`，`:21`），形如：
     ```json
     {
       "type": "object",
       "properties": {
         "params": {
           "type": "object",
           "properties": {
             "address": {"type": "string", "description": "..."}
           },
           "required": ["address"]
         }
       },
       "required": ["params"]
     }
     ```
   - **一个 Java lambda**（这是后面真正被回调的函数）：
     ```java
     (ReactCliToolInput input) -> ReactRunSession.callWithSession(runSession,
             () -> reactCliToolInvoker.invoke(cli, input))
     ```

3. 这些 `ToolCallback` 连同 system prompt（项目提示词 + 技能提示词 + ReAct 工作方式说明 + "当前已挂载 N 个 CLI 工具"）一起传给 `ReactAgent`（`ReactAgentRunner.java:108`）。

### 第 1 步：模型"看到"工具并决定调用

`ReactAgent`（Spring AI Alibaba 的 graph agent）每次请求大模型时，会把所有 tool 的 `name + description + inputSchema` 按 OpenAI 的 `tools=[...]` 字段一起发给 LLM。

模型在用户消息 + system prompt 上下文中**自主推理**，决定"我要调 `react_cli_gaode_geocode`"，此时它输出的不是普通文本，而是结构化的 `tool_calls`：

```json
{
  "tool_calls": [{
    "id": "call_xxx",
    "function": {
      "name": "react_cli_gaode_geocode",
      "arguments": "{\"params\":{\"address\":\"北京天安门\"}}"
    }
  }]
}
```

> **到这里模型只输出了 JSON，它自己根本没有、也无法发起任何 HTTP**——它只是表达了"想调用"的意图。

### 第 2 步：Spring AI 解析 tool_call 并回调 Java lambda

这一跳由框架完成，不在项目代码里：

1. `ReactAgent` / `ChatModel` 收到模型响应，发现里面有 `tool_calls`。
2. 按 `function.name`（`react_cli_gaode_geocode`）在已注册的 `ToolCallback` 列表里找到匹配的那个 `FunctionToolCallback`。
3. 把 `function.arguments` 这段 JSON **反序列化成 `ReactCliToolInput`**（builder 里指定了 `.inputType(ReactCliToolInput.class)`），得到 `input.params = {address: "北京天安门"}`。
4. 调用注册的 lambda —— 从这里开始进入项目自己的代码。

### 第 3 步：进入项目回调

`ReactCliToolFactory.java:54` 的 lambda 做两件事：

- `ReactRunSession.callWithSession`（`ReactRunSession.java:41`）把当前 run 的 `ReactRunSession` 绑到 ThreadLocal。这一步必须有——Spring AI 回调工具时可能切线程，而 `ReactCliToolInvoker` 要靠 `ReactRunSession.current()` 拿到 runId、userMessage、accessToken 等上下文。
- 然后进入 `ReactCliToolInvoker.invoke(cli, input)`（`ReactCliToolInvoker.java:40`）。

### 第 4 步：调度 + 真正发 HTTP

`ReactCliToolInvoker.invoke` 里：

1. 建任务记录（`agent_run_task_info`），标记 Running。
2. `invokeWithRetry`（`:110`）—— 90s 超时 + 最多 3 次重试，按 `commandType` 分发：
   - `API` → `CliCommandExecutor.executeApi`（`:123`）
   - `MCP` → `CliCommandExecutor.executeMcp`

`CliCommandExecutor.executeApi`（`CliCommandExecutor.java:31`）里：

1. 从 DB 拿 `AgentToolDefinition`（url / method / schemaCode）和 `AgentCliParam` 列表。
2. `CliParamBinder.bind`（`:49`）把**模型传入的 `input.params`** 和 **DB 中定义的参数默认值 / 上下文**合并成最终的 `boundParams`。
3. 调用 `ApiToolExecutor.execute(tool, boundParams, accessToken)`。

`ApiToolExecutor.execute`（`ApiToolExecutor.java:36`）—— **真正发 HTTP 的地方**：

1. 拼 URL：`AgentApiSchemaConfig.baseUrl + tool.url`，并用参数替换 `{from}/{to}` 这类路径占位符（`resolvePathParams`，`:116`）。
2. GET → 参数放 query；POST → 参数放 JSON body。
3. 内部 URL（非 http 开头）注入 `x-access-titc-c-token` 和 `x-hotel-code` 请求头（`buildHeaders`，`:161`）。
4. `WebClient ... .block(Duration.ofSeconds(30))` —— **这一行才是真正把 HTTP 发出去的地方**（POST 在 `:61`，GET 在 `:76`）。

MCP 类型则走 `McpToolExecutor.execute`（`McpToolExecutor.java:23`）：从 `McpClientPool` 拿对应 `mcp_server_id` 的 `McpStdioClient`，`client.callTool(tool.getMcpToolName(), params)`（`:42`）通过 stdio 拉起子进程调用 MCP 工具。

### 第 5 步：结果回流给模型

HTTP / MCP 的响应字符串一路返回：

```
ApiToolExecutor → CliCommandExecutor → ReactCliToolInvoker → lambda
```

Spring AI 拿到这个字符串后，作为一条 `role=tool` 的消息**追加进对话历史，再次请求模型**。模型看到工具结果后继续推理——要么再调一个工具（回到第 1 步），要么输出最终答案。这就是 ReAct 的多轮工具调用循环。

`ReactCliToolInvoker` 在成功时会 `taskInfoService.markSuccess`（记录耗时与结果摘要），失败时 `markFailed` 并把错误描述作为工具结果返回给模型（`:102`），让 Agent 自行处理失败而不是让整个对话卡死。

---

## 三、一图流

```
用户消息
   │
   ▼
ReactAgentRunner  ──把DB里的CLI包成 ToolCallback──►  ReactAgent
                                                         │ tools=[...] + userMessage
                                                         ▼
                                                     【LLM】自主决策
                                                         │ 输出 tool_calls(JSON)
                                                         ▼
                                              Spring AI 按 name 匹配 ToolCallback
                                                         │ arguments 反序列化成 ReactCliToolInput
                                                         ▼
                                              FunctionToolCallback 里的 lambda
                                                         │ ReactRunSession.callWithSession 绑上下文
                                                         ▼
                                              ReactCliToolInvoker.invoke
                                                         │ 建任务 / 90s超时 / 3次重试
                                                         ▼
                                              CliCommandExecutor.executeApi / executeMcp
                                                         │ CliParamBinder 合并参数
                                                         ▼
                                       ┌─────────────────┴─────────────────┐
                                       ▼                                   ▼
                              ApiToolExecutor.execute              McpToolExecutor.execute
                              ★ WebClient 发 HTTP                  ★ stdio 调 MCP 子进程
                                       │                                   │
                                       └─────────────────┬─────────────────┘
                                                         │ 响应字符串
                                                         ▼
                                              作为 tool 消息喂回 LLM → 继续推理
                                                       （多轮 ReAct 循环）
```

---

## 四、关键设计点

1. **决策与执行分离**：调不调、调哪个、传什么参数，完全由模型在 ReAct 循环里自主判断；后端只负责"把 DB 里配置的 CLI 暴露成工具"和"在模型发起调用后真正去执行"。
2. **契约对接**：模型与后端之间靠 tool 的 `name`（`react_cli_` 前缀）和 `inputSchema`（由 `AgentCliParam` 生成）这套契约解耦——新增一个 CLI 只需写 SQL 配置 `agent_cli_command` / `agent_cli_param` / `agent_tool_definition`，不用改 Java。
3. **上下文靠 ThreadLocal**：`ReactRunSession` 用 ThreadLocal 在回调链里传递 runId / accessToken / userMessage，因为 Spring AI 回调工具时可能切线程，需要 `callWithSession` 重新绑定。
4. **超时与容错**：单次工具调用 90s 超时（MCP 要拉起子进程+渲染网页，耗时较长），最多重试 3 次；工具失败不抛异常打断对话，而是把错误信息作为工具结果返回给模型继续处理。
5. **可观测**：每次工具调用都在 `agent_run_task_info` 落一条任务记录，含状态（Running/Success/Failed）、耗时、结果摘要，便于排查。

---

## 五、相关文档

- `docs/react-orchestration-design.md` — ReAct 编排整体设计
- `docs/cli-capability-config-guide.md` — CLI 能力配置指南（如何写 SQL 接入新 CLI）
- `docs/migration-phase4-cli.sql` / `migration-phase5-mcp.sql` — CLI / MCP 相关的表结构迁移
