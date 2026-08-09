# AI Foundation 项目文档

## 项目概述

AI Foundation 是一个 Agent 编排平台，按项目空间管理 Chat/Embedding 模型、CLI、Skill、知识库等能力，对外提供会话、消息、Agent 编排、RAG 检索等能力，并配套管理后台与 SDK。

当前进度：**阶段 0（工程骨架与基础设施）**、**阶段 1（管理后台基础与项目配置）**、**阶段 2（会话、消息与最小 Chat 闭环）**、**阶段 3（Run、事件流与 Agent 生命周期）** 均已完成。

## 技术栈与版本

| 维度 | 选型 |
| --- | --- |
| 语言 | Java 17（Corretto 17） |
| 构建 | Maven 3.6.3 多模块 |
| Web | Spring Boot 3.5.10 + WebFlux（Netty） |
| AI 编排 | Spring AI 1.1.2 + Spring AI Alibaba 1.1.2.3（graph-core Agent Framework） |
| 模型接入 | Spring AI OpenAI 兼容 starter（可接 DeepSeek/通义/本地等 OpenAI 协议端点） |
| ORM | MyBatis-Plus 3.5.16（boot3-starter + jsqlparser），逻辑删除 |
| 数据库 | MySQL 9.x（utf8mb4） |
| 缓存 | Redis 8.x（StringRedisTemplate） |
| 对象映射 | Lombok + MapStruct 1.6.3 |
| 前端 | Vue 3.5 + Element Plus 2.9 + Vite 6 + TypeScript + Pinia + Vue Router 4 + Axios |

> 版本管理说明：`spring-ai-alibaba-bom` 托管 Alibaba 自身制品，`spring-ai-bom` 托管 Spring AI 核心制品（两者均 import，不单独维护版本）。Agent 编排统一走 Alibaba graph-core，模型接入走 OpenAI 兼容 starter 以支持多供应商。

## 模块架构

```
ai-foundation-parent (pom)
├── ai-foundation-com      公共组件：统一响应、错误码、业务异常、枚举、常量、trace
├── ai-foundation-dal      数据访问：PO 实体、Mapper、MyBatis-Plus 配置
├── ai-foundation-facade   对外接口：DTO（请求/响应对象）、校验注解
├── ai-foundation-biz      业务服务：领域 Service（继承 IService）+ MapStruct 转换器
├── ai-foundation-mediator  编排层：MedService（业务编排）、AdminAuth、AgentModelResolver、AI 配置
└── ai-foundation-gateway   Web 网关：启动入口、Controller、WebFilter、CORS、全局异常处理、配置
```

分层调用链：`Controller(gateway) → MedService(mediator) → Service(biz) → Mapper(dal) → MySQL`。WebFlux 下阻塞 JDBC 调用通过 `MonoUtils.fromBlocking`（boundedElastic 调度器）包装为响应式。

## 已实现能力

### 阶段 0
- Maven 多模块工程骨架，`mvn -Pqa -DskipTests compile` 通过。
- `GatewayApplication` 可启动，`/actuator/health` 返回 UP。
- MySQL 数据源 + MyBatis-Plus 逻辑删除配置。
- Redis 客户端 + key 常量（登录态、缓存）。
- Spring AI OpenAI 兼容模型配置（`spring.ai.openai.*`）。
- 配置项：`agent.ai.models`、`agent.retrieval`、`agent.orchestration-mode`。
- CORS（`CorsWebFilter`）、环境 profile：test/qa/stage/product。
- 统一响应 `ApiResponse`、错误码 `ResultCode`、业务异常 `BusinessException`、trace 工具。

### 阶段 1
- **登录鉴权**：`POST /admin/auth/login`（配置账号），`AdminAuthWebFilter` 校验 `x-admin-token`，token 存 Redis（8h TTL），`/admin/*` 未授权返回 401。
- **项目管理**：`agent_project` 表；分页/详情/新增/修改/删除接口；`project_code` 唯一校验；软删。
- **Project 固定系统提示词**：`agent_project.system_prompt` 支持项目维度固定提示词，`prompt_variables` 定义项目所需上下文变量；创建会话时校验并固化变量快照，调用模型时由 Spring AI Advisor 注入最终系统提示词。
- **模型配置管理**：`agent_model_config` 表；分页/详情/新增/修改/删除；按项目筛选；模型类型 CHAT/EMBEDDING。
- **AgentModelResolver** 雏形：按项目读取启用模型，未配置时回退系统默认（`agent.ai.models`）。
- **管理后台前端**：Vue3 + Element Plus；登录页（token 存 localStorage）；请求拦截器自动携带 `x-admin-token`；可折叠侧边栏布局；项目配置页（列表/搜索/分页/增删改弹窗/状态标签）；模型配置页（按项目筛选/增删改）。


### 阶段 2
- **会话与消息表**：`agent_conversation_info`（会话编码、项目/产品上下文、通用变量快照、模型信息、置顶、状态）+ `agent_message_info`（角色、内容、token 数、耗时、附件、客户端 IP）。
- **会话管理**：`AgentConversationMedService` 实现创建会话（按 productCode 查项目）、分页列表、详情（含消息）、删除（级联软删消息）、清空消息。`AgentMessageMedService` 实现最近消息、滚动消息。
- **会话编码生成**：`ConversationCodeGenerator`（`conv_` + 时间戳 + 随机串）。
- **同步 Chat**：`AiChatMedService.syncChat`，使用 Spring AI ChatClient 调用 OpenAI 兼容接口；支持 `modelName`、`systemPrompt`、`temperature`、`maxTokens`；加载历史消息构建上下文，并通过 `ProjectSystemPromptAdvisor` 注入项目系统提示词、会话变量上下文和摘要；保存 user/assistant 消息。
- **流式 Chat（SSE）**：`AiChatMedService.streamChat`，返回 `Flux<ServerSentEvent>`；事件类型 start/token/complete/error；流式完成后保存完整 assistant 消息。
- **ChatClient 手动配置**：`AiClientConfig` 手动创建 `OpenAiApi` → `OpenAiChatModel` → `ChatClient` Bean（`@ConditionalOnMissingBean` 不覆盖自动配置）。
- **管理后台前端**：新增「会话管理」页（分页/详情/清空/删除）+「Playground」页（选择项目→创建会话→流式对话，SSE 逐 token 展示）。Playground 已重构为对话后台测试台，支持历史会话侧栏、System Prompt 临时覆盖、Run 状态面板、SSE 事件时间线，以及解析模型输出中的 `<think>...</think>` 思考链路。

### 阶段 3
- **Run 与事件流**：`AgentRunMedService` + `AgentOrchestrator`，Run 为一次对话执行单元，支持创建/流式事件订阅/取消/确认；SSE 事件类型覆盖 run_start、chat_start、request_messages、user_message、chat_reasoning、chat_token、chat_complete、run_complete、run_error、run_cancelled。
- **滚动增量摘要**：`ChatHistoryComposer` + `ConversationSummaryService`，Redis 热缓存保留最近 N 轮对话原文（默认 5 轮），超出时最老一轮异步挤出并调用 LLM 增量更新摘要；摘要写入 `agent_conversation_info.summary`，最大 800 字符；组装对话历史时近期轮次用原文、远期用摘要，既保留长会话上下文又控制 Token 用量。
- **异步摘要线程池**：`SummaryAsyncConfig` 配置独立线程池（core 2/max 8/queue 200），`@Async` 执行增量摘要，不阻塞主对话流。

## 接口清单

### Run 管理（阶段 3）
- `POST /chat/runs/create`：创建 Run，返回 `runCode`，异步启动编排。
- `POST /chat/runs/events`（SSE）：订阅 Run 事件流。
- `POST /chat/runs/detail`：查询 Run 详情。
- `POST /chat/runs/cancel`：取消运行中的 Run。
- `POST /chat/runs/confirm`：人工确认（骨架）。

### Playground 测试台
- 页面路径：`/playground`，面向后台开发与调试人员。
- 交互流程：选择项目 → 新建或选择历史会话 → 输入可选 System Prompt → 发送用户消息 → 通过 `/chat/runs/create` + `/chat/runs/events` 订阅 Run 事件。
- 流式能力：使用浏览器 `EventSource` 消费 SSE，实时展示 `run_start`、`chat_start`、`request_messages`、`chat_token`、`chat_complete`、`run_complete`、`run_error`、`run_cancelled`。
- 提示词注入：模型调用前从当前会话的 `context_variables` 读取变量快照，替换 `agent_project.system_prompt` 中的 `{{变量}}`，再由 `ProjectSystemPromptAdvisor` 写入 Spring AI Prompt 的 system message。
- 调试可观测：后端在模型调用前通过 `request_messages` 推送真实请求消息栈（系统提示词、历史消息、当前用户消息）；右侧 Run Inspector 默认展示 Messages 视图，可查看真实发送内容、AI 思考与 AI 回复，Trace 视图保留原 Run 生命周期时间线。
- 思考链路：后端优先推送模型返回的 `reasoning_content` 为 `chat_reasoning` 事件；如模型仅在正文中输出 `<think>...</think>`，前端会解析该片段并在消息折叠区与右侧 Inspector 中展示。

| 方法 | 路径 | 说明 | 鉴权 |
| --- | --- | --- | --- |
| POST | `/admin/auth/login` | 管理员登录 | 否 |
| GET | `/admin/project/page` | 项目分页 | 是 |
| GET | `/admin/project/{id}` | 项目详情 | 是 |
| POST | `/admin/project` | 新增项目 | 是 |
| PUT | `/admin/project` | 修改项目 | 是 |
| DELETE | `/admin/project/{id}` | 删除项目（软删） | 是 |
| GET | `/admin/model/page` | 模型配置分页 | 是 |
| GET | `/admin/model/{id}` | 模型详情 | 是 |
| POST | `/admin/model` | 新增模型配置 | 是 |
| PUT | `/admin/model` | 修改模型配置 | 是 |
| DELETE | `/admin/model/{id}` | 删除模型配置 | 是 |
| GET | `/admin/conversation/page` | 会话分页 | 是 |
| GET | `/admin/conversation/{id}` | 会话详情（含消息） | 是 |
| DELETE | `/admin/conversation/{id}` | 删除会话（级联软删消息） | 是 |
| POST | `/admin/conversation/{id}/clear-messages` | 清空会话消息 | 是 |
| POST | `/chat/create` | 创建会话（SDK/Playground） | 否 |
| GET | `/chat/messages/{conversationCode}` | 获取会话消息 | 否 |
| POST | `/chat/sync` | 同步对话 | 否 |
| POST | `/chat/stream` | 流式对话（SSE） | 否 |
| GET | `/actuator/health` | 健康检查 | 否 |

默认管理员账号：`admin` / `admin123`（配置于 `application.yml` → `agent.admin.accounts`）。

## 数据库

- 库名：`ai_foundation`，用户：`ai_app` / `AiApp@2026`
- 初始化 DDL：`docs/full-schema-ddl.sql`（全量），已建 `agent_project`、`agent_model_config`、`agent_conversation_info`、`agent_message_info`。
- Project 提示词变量设计：`docs/project-prompt-variables-design.md`。变量在创建会话时传入并写入 `agent_conversation_info.context_variables`，后续对话通过 `conversationCode` 复用会话变量。
- 约定：统一 `id`、`is_delete`、`create_time`、`update_time`；业务表含 `state`、`create_user`、`modify_user`；逻辑删除；无物理外键。

## 编码规范

- 后端包名：`com.ai.foundation.<模块>.<子域>`；PO 用 `@TableName/@TableId/@TableLogic`；Service 继承 `IService`，仅声明自定义方法，避免重写 ServiceImpl 同名方法导致递归。
- WebFlux 下阻塞调用统一经 `MonoUtils.fromBlocking` 包装；空 token 同步拦截，有效 token 走 `switchIfEmpty` 处理失效。
- 统一响应 `ApiResponse<T>`（success/code/message/data/traceId）；业务异常 `BusinessException(ResultCode)`；`@RestControllerAdvice` 全局处理。
- 前端：`@/` 别名指向 `src`；API 返回值经响应拦截器解包；401 自动跳登录页并清 token。

## 运行方式

### 中间件
```bash
brew services start mysql   # MySQL 9.x，root 无密码
brew services start redis    # Redis 8.x
# 建库建表（需用 python 调 mysql，shell 的 mysql 命令被沙箱拦截）
```

### 后端
```bash
cd backend
JAVA_HOME=<corretto-17> mvn -Pqa -DskipTests install
JAVA_HOME=<corretto-17> mvn -pl ai-foundation-gateway spring-boot:run -Pqa
# 健康检查：curl http://localhost:8080/actuator/health
```

### 前端
```bash
cd frontend
npm install
npm run dev          # http://localhost:5173 ，Vite 代理 /admin、/chat → :8080
npm run build        # 类型检查 + 生产构建
```
浏览器打开 `http://localhost:5173`，使用 `admin / admin123` 登录。

### Playground 前端（assistant-ui）
```bash
cd frontend-playground
npm install
npm run dev          # http://localhost:5174 ，Vite 代理 /admin、/chat -> :8080
npm run build        # 类型检查 + 生产构建
```
基于 assistant-ui（React 19 + Vite 6 + Tailwind v4）的独立 Playground 调试台，对接现有 Run 事件模型。
- 对话流式：通过 `useExternalStoreRuntime` 适配器消费 `/chat/runs/events` SSE，`chat_token` 逐 token 渲染。
- 思考过程：解析模型输出中的 `<think>...</think>` 片段为 reasoning 消息 part，流式实时展示。
- Run Inspector：右侧面板提供 Messages / Trace 双视图；Messages 展示真实模型请求消息栈、AI 思考、AI 回复，Trace 展示 Run 生命周期事件并折叠 chat_token 噪音聚合计数。
- 会话管理：左侧侧栏选择项目、新建/选择历史会话、System Prompt 临时覆盖。

核心对接逻辑在 `frontend-playground/src/runtime/aui-runtime.ts`（`useAiRuntime` 钩子），通过 `onNew` 回调串联 createRun + EventSource。


### 访问地址
- 后端：http://localhost:8080（健康检查 `GET /actuator/health` 返回 `{"status":"UP"}`）
- 前端：http://localhost:5173（浏览器访问，用 `admin` / `admin123` 登录；Vite 已代理 `/admin` → `:8080`）
- Playground 前端：http://localhost:5174（React + assistant-ui，登录同 `admin` / `admin123`；Vite 代理 `/admin`、`/chat` -> `:8080`）
- MySQL：localhost:3306，库名 `ai_foundation`，用户 `ai_app` / `AiApp@2026`
- Redis：localhost:6379
- 模型服务：Ollama `http://localhost:11434`（OpenAI 兼容，当前使用 `deepseek-r1:1.5b`）

## 后续阶段

阶段 4 起将实现：CLI/API/Page 能力配置与执行、Skill、知识库 RAG、Plan/ReAct 编排、SDK、文件附件等（详见 `docs/implementation-roadmap.md`）。
