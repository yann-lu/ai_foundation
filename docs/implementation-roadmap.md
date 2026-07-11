# 从零实现任务拆分

## 拆分原则

- 每一步只交付一个可自测的阶段性能力，避免一次实现过多模块。
- 表结构、后端能力、前端页面、SDK 能力同步推进，避免只有接口没有配置入口。
- 每一步完成后必须自测通过，再进入下一步。
- 优先搭出最小可运行闭环，再逐步增强 Agent、RAG、Skill、CLI、文件、权限、后台能力。
- 所有表默认使用 MySQL + MyBatis-Plus，包含 `id`、`is_delete`、`create_time`、`update_time`；业务表根据需要增加 `state`、`create_user`、`modify_user`。

## 阶段 0：工程骨架与基础设施

### 0.1 初始化多模块后端工程

目标：搭出可编译、可启动的后端工程骨架。

表结构：无。

后端任务：

- 创建 Maven parent 工程。
- 创建模块：`ai-foundation-com`、`ai-foundation-dal`、`ai-foundation-biz`、`ai-foundation-facade`、`ai-foundation-mediator`、`ai-foundation-gateway`。
- 配置 Java 17、Spring Boot 3、Spring AI、MyBatis-Plus、Lombok、MapStruct。
- `gateway` 引入 WebFlux、Validation。
- `mediator` 引入 Spring AI ChatClient/OpenAI、Milvus store、Tika document reader、Spring AI Alibaba Agent Framework。
- 增加环境 profile：`test`、`qa`、`stage`、`product`。
- 增加统一响应、错误码、业务异常、trace 工具、公共枚举、常量包。

前端任务：无。

自测标准：

- `mvn -Pqa -DskipTests compile` 通过。
- `GatewayApplication` 能启动，健康检查接口返回成功。

### 0.2 接入基础配置、数据库、Redis、对象存储

目标：完成运行所需中间件配置。

表结构：无新增业务表。

后端任务：

- 配置 MySQL 数据源、MyBatis-Plus 逻辑删除。
- 配置 Redis 客户端与基础 key 常量。
- 配置对象存储抽象，用于附件和生成文件上传。
- 配置 Spring AI OpenAI 兼容模型接口。
- 增加 `agent.ai.models`、`agent.retrieval`、`agent.orchestration-mode` 配置项。
- 增加 CORS、SSE 基础配置。

前端任务：无。

自测标准：

- 应用启动不报数据源、Redis、模型 Bean 缺失。
- 启动日志能确认环境配置加载正常。

## 阶段 1：管理后台基础与项目配置

### 1.1 管理后台登录与鉴权

目标：先让后台有可访问入口，并保护 `/admin/*` 接口。

表结构：暂不建用户表，使用配置账号或固定 token。

后端任务：

- 实现 `AdminAuthController`：`POST /admin/auth/login`。
- 实现 `AdminAuthWebFilter`：校验 `x-admin-token`。
- 返回登录 token、用户昵称等基本信息。
- `/admin/*` 未携带 token 时返回未授权。

前端任务：

- 创建 Vue3 + Element Plus 管理后台工程。
- 实现登录页。
- 登录成功后保存 token 到 localStorage。
- 请求拦截器自动携带 `x-admin-token`。
- 实现后台基础布局：菜单、顶部栏、内容区。

自测标准：

- 未登录访问 `/admin/*` 返回未授权。
- 登录成功后可访问后台首页。
- 刷新页面 token 仍生效。

### 1.2 项目表与项目管理

目标：完成项目空间管理，后续所有能力按项目挂载。

表结构：

- 新建 `agent_project`：项目名称、项目编码、描述、状态、创建人、修改人、软删、时间字段。
- 建议唯一约束：`uk_project_code(project_code, is_delete)`。

后端任务：

- 创建 `AgentProject` PO、DAO、Biz。
- 实现 `AgentProjectMedService`。
- 实现管理接口：分页、列表、详情、新增、修改、删除。
- 新增时校验 `project_code` 唯一。
- 删除项目时暂不级联删除能力，仅标记项目软删。

前端任务：

- 新增“项目配置”菜单。
- 实现项目列表、搜索、分页。
- 实现新增/编辑弹窗。
- 实现启用/停用状态展示。
- 实现删除确认。

自测标准：

- 能新增项目并在列表展示。
- 重复 `projectCode` 不能新增。
- 编辑后刷新页面数据仍正确。
- 删除后列表不再展示。

### 1.3 模型配置管理

目标：支持项目维度配置 Chat/Embedding 模型。

表结构：

- 新建 `agent_model_config`：项目 ID、模型名称、模型类型、状态、创建人、修改人、软删、时间字段。
- 建议索引：`idx_project_model_type(project_id, model_type, state, is_delete)`。

后端任务：

- 创建 PO、DAO、Biz。
- 实现模型配置管理接口：分页、详情、新增、修改、删除。
- 实现 `AgentModelResolver` 雏形：按角色读取配置默认模型，支持项目覆盖。
- 支持模型类型：`CHAT`、`EMBEDDING`。

前端任务：

- 新增“模型配置”菜单。
- 支持按项目筛选模型。
- 支持新增/编辑模型名称、类型、状态。

自测标准：

- 项目下可维护多个模型。
- 停用模型不参与后续模型解析。
- 不配置项目模型时使用系统默认模型。

## 阶段 2：会话、消息与最小 Chat 闭环

### 2.1 会话与消息表

目标：具备保存会话和消息历史的能力。

表结构：

- 新建 `agent_conversation_info`：项目 ID、产品编码、集团编码、酒店编码、会话编码、用户 ID、标题、摘要、模型提供商、模型名称、置顶、最后消息时间、状态、软删、时间字段。
- 新建 `agent_message_info`：会话 ID、角色、内容、token 数、耗时、附件 JSON、客户端 IP、状态、软删、时间字段。
- 建议索引：`idx_conversation_user(project_id, user_id, state, is_delete)`、`uk_conversation_code(conversation_code, is_delete)`、`idx_message_conversation(conversation_id, create_time, is_delete)`。

后端任务：

- 创建会话/消息 PO、DAO、Biz。
- 实现会话编码生成工具。
- 实现 `AgentConversationMedService`：创建会话、列表、删除、清空消息。
- 实现 `AgentMessageMedService`：最近消息、滚动消息、最近轮次、滚动轮次。
- 会话创建时根据 `productCode` 找到项目。

前端任务：

- 管理后台增加“会话管理”页面。
- 支持会话分页、详情、删除、清空消息。
- SDK 侧暂不实现 UI，只预留接口类型。

自测标准：

- 创建会话后数据库有会话记录。
- 写入多条消息后可按时间滚动查询。
- 清空消息后会话仍在，消息不可见。

### 2.2 同步 Chat 能力

目标：实现最小模型调用，用户发一句，AI 返回一句。

表结构：复用会话与消息表。

后端任务：

- 实现 `AiChatMedService.syncChat`。
- 使用 Spring AI ChatClient 调用 OpenAI 兼容接口。
- 支持传入 `modelName`、`systemPrompt`、`userMessage`、`temperature`、`maxTokens`。
- 实现 Prompt 上下文格式化基础版：系统上下文 + 当前用户消息。
- 保存用户消息和助手消息。

前端任务：

- SDK playground 或简单页面实现：选择项目/产品上下文、输入消息、展示回复。
- 管理后台会话详情可看到 user/assistant 消息。

自测标准：

- 调用接口能返回模型回复。
- 数据库保存用户消息和助手消息。
- 模型不可用时返回明确错误，不吞异常。

### 2.3 流式 Chat 与 SSE

目标：支持 token 流式返回。

表结构：复用会话与消息表。

后端任务：

- 实现 `AiChatMedService.streamChat`。
- 增加流式事件 DTO：start、token、complete、error。
- Gateway 使用 `MediaType.TEXT_EVENT_STREAM_VALUE` 返回 Flux。
- 流式完成后保存完整助手消息。
- 增加 `SseStreamWebFilter`，保证 SSE 不被普通响应包装破坏。

前端任务：

- SDK playground 支持流式展示 token。
- 支持 loading、错误状态。

自测标准：

- 前端能逐字/分片显示回复。
- 刷新会话详情能看到完整助手消息。
- 模型异常时前端收到 error 事件。

### 2.4 可嵌入 Chat Widget 基础版

目标：完成 SDK 的最小可嵌入聊天窗口。

表结构：无新增。

后端任务：

- 确认 `/chat/create`、`/chat/list`、`/chat/messages/*`、同步/流式接口协议稳定。
- 增加跨域 headers：`x-access-titc-c-token`、`x-bloc-code`、`x-hotel-code`。

前端任务：

- 创建 `ai-foundation-sdk`。
- 实现 `AiChat.init({ token })`。
- 实现 `AiChat.open({ productCode, blocCode, hotelCode })`。
- 使用 Shadow DOM 隔离样式。
- 实现右下角浮动按钮、抽屉式会话面板、消息列表、输入框。
- 支持关闭、销毁、更新上下文。

自测标准：

- 任意测试页面引入 SDK 后能打开聊天窗口。
- 消息发送后能流式展示 AI 回复。
- Shadow DOM 样式不污染宿主页面。

## 阶段 3：Run、事件流与 Agent 生命周期

### 3.1 Run 表与创建 Run

目标：把一次对话请求抽象为 Run，为后续 Agent 编排做基础。

表结构：

- 新建 `agent_run`：会话 ID、Run 编码、traceId、产品编码、触发消息 ID、Run 类型、任务状态、token 消耗、成本、状态、软删、时间字段。
- 建议索引：`uk_run_code(run_code, is_delete)`、`idx_conversation_run(conversation_id, create_time, is_delete)`。

后端任务：

- 创建 Run PO、DAO、Biz。
- 实现 Run 编码生成工具。
- 实现 `AgentRunMedService.createRun`。
- 创建 Run 时保存用户消息，并异步启动执行。
- 增加 Run 状态枚举。

前端任务：

- SDK 发送消息改为调用 `/chat/runs/create`。
- 保存返回的 `runCode`。

自测标准：

- 创建 Run 后 `agent_run` 有记录。
- Run 与会话、触发消息关联正确。

### 3.2 Run 事件总线与 SSE 订阅

目标：Run 执行过程通过事件总线推送给前端。

表结构：复用 `agent_run`。

后端任务：

- 实现 `RunEventBus`。
- 实现 `RunEventEmitter`。
- 实现 `/chat/runs/events`。
- 事件类型包括：run_start、chat_start、chat_token、run_complete、run_error。
- Run 完成后关闭事件流。

前端任务：

- SDK 发送消息后订阅 `/chat/runs/events`。
- 根据事件展示 token、完成状态、错误状态。

自测标准：

- 前端只依赖 Run events 即可完成一轮对话展示。
- 后端异常时 Run 状态和前端错误一致。

### 3.3 直接 Chat Run 执行器

目标：让 Run 先支持直接 Chat，形成稳定生命周期闭环。

表结构：复用 Run、会话、消息。

后端任务：

- 实现 `AgentOrchestrator` 基础版。
- 构建 `AgentExecutionContext`：项目、产品、集团、酒店、用户。
- 调用 `AiChatMedService.streamChat`。
- 通过 RunEventEmitter 发送 token。
- 完成后保存助手消息，更新 Run 状态。
- 增加 `ConversationSummaryService` 基础版。

前端任务：

- SDK 展示 Run 生命周期状态。
- 管理后台会话详情显示 Run 编码和状态。

自测标准：

- 创建 Run、订阅事件、保存消息、Run 完成全链路通过。
- 多个会话并发发送消息互不串流。

### 3.4 Run 详情、取消、确认接口骨架

目标：补齐 Run 管理接口，为 Plan/ReAct 做准备。

表结构：复用 `agent_run`。

后端任务：

- 实现 `/chat/runs/detail`。
- 实现 `/chat/runs/cancel`。
- 实现 `/chat/runs/confirm` 骨架，当前直接返回无待确认任务。
- 取消 Run 时更新状态并推送取消/完成事件。

前端任务：

- SDK 支持中止当前回答。
- 管理后台会话详情展示 Run 详情。

自测标准：

- 运行中的 Run 可取消。
- 取消后前端停止 loading。
- Run 详情能展示状态和触发消息。

## 阶段 4：CLI/API/Page 能力配置与执行

### 4.1 CLI 命令与项目绑定

目标：配置 Agent 可用的命令能力。

表结构：

- 新建 `agent_cli_command`：命令前缀、分组、动作、唯一命令名、模板、描述、命令类型、状态、创建人、修改人、软删、时间字段。
- 新建 `agent_cli_param`：CLI ID、参数名、参数 flag、参数类型、数组元素类型、是否必填、描述、默认值、排序、父参数名、软删、时间字段。
- 新建 `agent_project_cli_rel`：项目 ID、CLI ID、状态、创建人、修改人、软删、时间字段。
- 建议唯一约束：`uk_command_name(command_name, is_delete)`、`uk_project_cli(project_id, cli_id, is_delete)`。

后端任务：

- 实现 CLI CRUD。
- 保存 CLI 时同步保存参数列表。
- 实现项目绑定 CLI。
- 实现 `CapabilityRegistry` 基础版：按项目加载已启用 CLI。
- 校验 `command_name` 唯一。

前端任务：

- 管理后台新增“CLI 管理”页面。
- 支持 CLI 列表、详情、新增、编辑、删除。
- 支持参数动态增删改，包括对象子参数。
- 项目配置页支持挂载/取消挂载 CLI。

自测标准：

- 新增 API 型和 PAGE 型 CLI 基础信息。
- 参数列表保存后再次编辑不丢失。
- 项目绑定后能力目录能加载到该 CLI。

### 4.2 API Tool 表与 HTTP 调用

目标：让 API 型 CLI 能调用真实业务 HTTP 接口。

表结构：

- 新建 `agent_tool_definition`：CLI ID、工具名、描述、URL、Method、鉴权类型、Schema 编码、请求 JSON Schema、响应 JSON Schema、软删、时间字段。
- 新建 `agent_tool_param`：Tool ID、参数名、参数类型、是否必填、描述、创建人、修改人、软删、时间字段。
- 新建 `agent_tool_call_log`：Task ID、CLI ID、Tool ID、请求参数 JSON、响应内容、耗时、状态、软删、时间字段。

后端任务：

- CLI 保存页支持 API Tool 配置。
- 实现 `ApiToolExecutor`。
- 实现 HTTP GET/POST 调用。
- 支持透传用户 access token。
- 写入 `agent_tool_call_log`。
- 实现 `ApiToolResultInterpreter` 基础版：把接口结果压缩成模型可读摘要。

前端任务：

- CLI 编辑页增加 API Tool 配置区。
- 支持填写 URL、Method、鉴权类型、请求/响应 Schema。
- 支持测试调用按钮，手动输入参数并查看响应。

自测标准：

- 配置一个 mock HTTP API 后可调用成功。
- 调用日志包含参数、响应、耗时、状态。
- 接口失败时日志记录失败，并返回明确错误。

### 4.3 CLI 参数绑定

目标：让 Agent 能从用户消息、上下文、默认值中提取 API 参数。

表结构：复用 CLI 参数表。

后端任务：

- 实现 `CliParamBinder`。
- 支持默认值绑定。
- 支持从 `AgentExecutionContext` 绑定 `productCode`、`blocCode`、`hotelCode`。
- 支持 LLM 参数抽取。
- 支持从前序步骤结果提取参数。
- 缺失必填参数时返回明确错误或追问提示。

前端任务：

- CLI 参数编辑页支持默认值和必填项配置。
- 测试调用时展示最终绑定参数。

自测标准：

- 用户未提供酒店编码时能使用上下文酒店编码。
- 默认值能参与绑定。
- 必填参数缺失时不调用 API。

### 4.4 页面跳转 CLI

目标：支持 Agent 打开业务页面或弹窗。

表结构：

- 新建 `agent_page_definition`：CLI ID、页面名称、页面前缀、页面路由、描述、展示类型、目标类型、资源项目、资源 ID 列表、软删、时间字段。
- 新建 `agent_page_param`：页面 ID、参数名、参数类型、是否必填、描述、软删、时间字段。

后端任务：

- CLI 保存页支持 PAGE 配置。
- 实现 `NavigateExecutor`，生成跳转结果。
- 实现 `ResourcePermissionValidator` 基础版，可先按资源字段预留，后续接权限系统。
- `CliCommandExecutor.executeNavigate` 支持 PAGE 型 CLI。

前端任务：

- CLI 编辑页增加页面跳转配置区。
- SDK 支持展示跳转卡片。
- SDK 派发 `innerLink` 事件，事件包含 `title`、`page`、`display`、`target`、`data`。

自测标准：

- 配置一个页面 CLI 后，调用返回跳转协议。
- SDK 接收到事件并能由宿主监听。
- 页面参数能通过用户消息或前序结果绑定。

## 阶段 5：OpenAPI Schema 与 CLI 批量生成

### 5.1 OpenAPI Schema 管理

目标：管理外部系统 OpenAPI 文档。

表结构：

- 新建 `agent_api_schema_config`：Schema 编码、名称、URL、内容、资源路径、Base URL、命令前缀、状态、创建人、修改人、软删、时间字段。

后端任务：

- 实现 Schema CRUD。
- 实现 `ApiSchemaContentProvider`：优先 classpath 资源，其次配置内容。
- 实现 `OpenApiSchemaResolver`：解析 paths、method、operationId、参数、requestBody、response。
- 提供接口：列出 operation、解析 operation、按 URL 解析 operation。

前端任务：

- 管理后台新增“API Schema”页面。
- 支持新增/编辑 Schema。
- 支持查看 operation 列表。
- 支持按 URL/operationId 查询解析结果。

自测标准：

- 上传或配置一个 OpenAPI JSON 后能解析出接口列表。
- 解析结果包含 method、path、operationId、参数和 schema。

### 5.2 CLI 批量生成预览与提交

目标：从 OpenAPI operation 预览并生成 CLI、参数、Tool。

表结构：

- 新建 `agent_cli_batch_job`：任务编号、项目 ID、Schema ID、Schema 编码、总数、完成数、成功数、更新数、失败数、状态、错误信息、创建人、修改人、软删、时间字段。
- 新建 `agent_cli_batch_item`：批次 ID、HTTP 方法、OpenAPI 路径、operationId、完整 URL、匹配键、预览命令名、生成后 CLI ID、状态、错误信息、软删、时间字段。

后端任务：

- 实现 `CliCommandGenerator`。
- 根据 schemaCode、method、path、operationId 生成 commandName。
- 生成 CLI 参数、Tool URL、请求/响应 Schema。
- 实现批量预览接口，只生成预览数据，不落 CLI 主表。
- 实现批量提交接口，创建或更新 CLI、参数、Tool，并自动绑定项目。
- 更新 job/item 状态与统计。

前端任务：

- “CLI 批量生成”页面支持选择项目和 Schema。
- 展示待生成 operation、预览 commandName、URL、参数数量。
- 支持勾选要提交的项。
- 展示批次状态、成功数、更新数、失败数、明细失败原因。

自测标准：

- 选择 Schema 后能预览多个接口。
- 不提交时不会创建 CLI。
- 提交后 CLI 管理页能看到生成的 CLI。
- 重复提交不会产生重复 CLI。

## 阶段 6：Skill 能力

### 6.1 Skill 定义、资源与项目绑定

目标：支持配置提示词型 Skill，并绑定项目和资源。

表结构：

- 新建 `agent_skill_definition`：技能名称、技能编码、描述、技能类型、system_prompt、扩展配置 JSON、状态、创建人、修改人、软删、时间字段。
- 新建 `agent_skill_resource`：Skill ID、资源类型、资源 ID、排序、创建人、修改人、软删、时间字段。
- 新建 `agent_project_skill_rel`：项目 ID、Skill ID、状态、创建人、修改人、软删、时间字段。
- 建议唯一约束：`uk_skill_code(skill_code, is_delete)`、`uk_project_skill(project_id, skill_id, is_delete)`。

后端任务：

- 实现 Skill CRUD。
- 支持 `PROMPT`、`WORKFLOW`、`MULTI_AGENT` 类型枚举，当前只执行 `PROMPT`。
- Skill 保存接口支持资源列表。
- 支持资源类型：`CLI`、`KB`、`MODEL`、`SKILL`。
- 实现项目绑定 Skill。
- 能力目录加载项目启用 Skill。

前端任务：

- 管理后台新增“Skill 管理”页面。
- 支持编辑 system_prompt。
- Skill 编辑页增加“关联资源”。
- 支持选择 CLI、知识库、模型、子 Skill。
- 项目配置页支持绑定 Skill。

自测标准：

- 新增 Skill 后能绑定到项目。
- Skill 资源保存后再次打开不丢失。
- 禁用 Skill 后能力目录不再出现。

### 6.2 PROMPT Skill 执行

目标：Agent 能调用 Skill 生成分析、报告、复杂业务回复。

表结构：复用 Skill 表。

后端任务：

- 实现 `SkillExecutor.executePromptSkill`。
- 拼接 Skill system_prompt、统一 Prompt 上下文、历史、附件、步骤结果。
- 将 Skill 关联 CLI 作为可用资源说明注入 Prompt。
- 将 Skill 关联知识库检索片段注入 Prompt。
- 支持 Skill 输出 `【建议调用】` 和 `【建议跳转】` 规范文本。

前端任务：

- Skill 管理页增加“测试执行”按钮。
- 输入测试问题，展示模型返回。

自测标准：

- 调用 Skill 返回符合 system_prompt 的内容。
- Skill 能看到会话上下文和附件上下文。
- 关联 CLI 和 KB 信息能被写入 Prompt。

## 阶段 7：RAG 知识库

### 7.1 知识库主表与项目绑定

目标：支持创建知识库并绑定项目。

表结构：

- 新建或扩展 `agent_knowledge_base`：项目 ID、知识库编码、名称、描述、业务域、标签 JSON、embedding 模型、分片策略、检索配置 JSON、Router 摘要、文档数、切片数、向量 collection、状态、创建人、修改人、软删、时间字段。
- 新建 `agent_project_kb_rel`：项目 ID、知识库 ID、绑定类型、引用 ID、优先级、是否默认、状态、创建人、修改人、软删、时间字段。
- 建议唯一约束：`uk_project_kb_code(project_id, kb_code, is_delete)`、`uk_project_kb_bind(project_id, kb_id, bind_type, ref_id, is_delete)`。

后端任务：

- 实现知识库 CRUD。
- 项目内 `kb_code` 唯一。
- 实现项目绑定知识库。
- 能力目录加载项目绑定知识库。

前端任务：

- 管理后台新增“知识库管理”页面。
- 支持知识库列表、详情、新增、编辑、删除。
- 项目配置页支持绑定知识库。

自测标准：

- 新增知识库后能绑定项目。
- 重复 `kbCode` 不能创建。
- 绑定默认知识库后能力目录可见。

### 7.2 知识文档上传与解析切片

目标：支持向知识库添加文档，并解析成切片。

表结构：

- 新建或扩展 `agent_knowledge_document`：知识库 ID、项目 ID、文档名、文档类型、MIME 类型、文件 URL、文件大小、文件 MD5、来源类型、来源 URL、解析状态、解析错误、解析版本、入库任务 ID、文档摘要、内容模式、字符数、切片数、资源数、元数据 JSON、状态、创建人、修改人、软删、时间字段。
- 新建或扩展 `agent_knowledge_chunk`：文档 ID、知识库 ID、项目 ID、衍生资源 ID、切片序号、内容类型、内容、锚文本、token 数、字符数、页码、Sheet 名、起止行、vectorId、embedding 模型、元数据 JSON、状态、软删、时间字段。

后端任务：

- 文档新增和上传接口。
- 文件上传到对象存储。
- 计算 MD5，避免同知识库重复上传相同文件。
- 实现 `KnowledgeIngestService`。
- 支持 txt、md、pdf、docx、xlsx、csv 基础解析。
- 实现 `KnowledgeTextChunker`。
- 替换文档切片时需要事务：删除旧切片、写入新切片。
- 更新文档解析状态、字符数、切片数。
- 更新知识库统计。

前端任务：

- 知识库详情页展示文档列表。
- 支持上传文档。
- 支持“开始解析/重新解析”。
- 文档详情展示切片预览。
- 展示解析状态、文档大小、切片数、错误信息。

自测标准：

- 上传文本文件后可解析出切片。
- 解析失败时文档状态和错误信息正确。
- 重新解析不会保留旧切片脏数据。

### 7.3 向量索引与 Milvus 检索

目标：切片可被向量检索召回。

表结构：复用知识库、文档、切片。

后端任务：

- 配置 EmbeddingModel。
- 实现 `KnowledgeIndexService`。
- 实现 `KnowledgeMilvusRepository`。
- 将切片写入 Milvus collection。
- 支持按 query 搜索 TopN。
- 支持 Milvus 不可用时关键词 fallback。

前端任务：

- 知识库详情页增加“检索测试”。
- 输入问题，展示命中的文档、切片、分数。

自测标准：

- 文档解析后能查到相关切片。
- Milvus 开启时使用向量检索。
- Milvus 关闭或异常时 fallback 不影响接口可用性。

### 7.4 文档衍生资源

目标：支持 PDF/DOCX 内嵌图片、扫描页、表格快照等资源记录。

表结构：

- 新建 `agent_knowledge_document_asset`：文档 ID、知识库 ID、项目 ID、资源类型、资源名称、文件 URL、MIME 类型、文件大小、文件 MD5、来源页码、同页序号、宽高、锚文本、OCR 文本、Caption 文本、处理状态、处理错误、关联切片 ID、元数据 JSON、状态、软删、时间字段。

后端任务：

- 实现 `KnowledgeEmbeddedImageExtractor`。
- 解析 PDF/DOCX 时提取图片资源。
- 图片上传对象存储。
- 将 OCR/Caption 结果作为切片或元数据。
- 更新文档 `asset_count`。

前端任务：

- 文档详情页展示衍生资源列表。
- 支持查看图片、OCR 文本、Caption。

自测标准：

- 含图片文档解析后生成 asset 记录。
- 图片可预览。
- 图片相关文本可被检索。

### 7.5 RAG 检索工具与日志

目标：Agent 能把知识库作为工具调用。

表结构：

- 新建或扩展 `agent_retrieval_log`：Task ID、Run ID、会话 ID、项目 ID、知识库 ID、候选 KB JSON、原始 query、改写 query、filter 表达式、召回内容快照、召回数量、重排数量、耗时、状态、错误信息、软删、时间字段。

后端任务：

- 实现 `KnowledgeRetriever`。
- 实现 `KnowledgeKbRouter`。
- 实现 `KnowledgeRetrievalLogWriter`。
- 实现 Plan 模式 `KnowledgeRagExecutor`。
- 实现 ReAct 模式 `ReactKnowledgeToolFactory` 和 `ReactKnowledgeToolInvoker`。

前端任务：

- Run 详情展示知识库检索步骤和命中片段。
- 知识库管理页展示检索日志分页。

自测标准：

- 用户问文档问题时 Agent 调用知识库并基于片段回答。
- 检索日志记录 query、命中片段和耗时。
- 多知识库时能自动选库或指定知识库。

## 阶段 8：Plan 编排

### 8.1 Task 表与能力目录

目标：支持 Run 拆分多步骤任务，并具备可规划能力目录。

表结构：

- 新建 `agent_task`：Run ID、步骤编码、能力类型、任务类型、引用资源 ID、指令、依赖步骤 JSON、步骤状态、结果引用、错误信息、是否异步、状态、软删、时间字段。

后端任务：

- 创建 Task PO、DAO、Biz。
- 实现 `RunManageService.createRunWithTasks`、批量插入任务、更新任务状态。
- 定义 `PlanStepDto`。
- 实现 `CapabilityCatalogItemDto`。
- `CapabilityRegistry` 汇总 CLI、Skill、KB。
- 实现能力目录缓存 `CapabilityCatalogCache`。

前端任务：

- Run 详情页展示任务步骤列表。
- 项目详情展示已绑定能力数量。

自测标准：

- Run 可创建多个 Task。
- Task 状态可独立更新。
- 项目绑定 CLI/Skill/KB 后能力目录完整。

### 8.2 能力检索、意图分流与 Planner

目标：从用户输入生成可执行计划。

表结构：复用 Task、能力配置表。

后端任务：

- 实现 `CapabilityRetriever`：目录较小时直接返回，较大时召回 TopK，再重排 TopN。
- 实现 `IntentTriageAgent`：输出 `chat|plan|unsupported`。
- 实现 `AgentPlanner`。
- Planner Prompt 限定输出 JSON 数组。
- 支持能力类型：api、navigate、skill、knowledge。
- 解析失败时 fallback 单步。
- 对页面跳转目标做二次校正。
- 生成 Task 并持久化。

前端任务：

- Run 详情展示路由模式、原因、Planner 原始输出、最终 Task。
- 后台增加“刷新缓存”按钮。

自测标准：

- 闲聊问题走 chat。
- “查询订单并打开详情”生成 API + navigate 两步。
- “制度怎么规定”生成 knowledge 步骤。
- refId 不得编造，必须来自能力目录。

### 8.3 Plan 步骤执行与确认

目标：按 Task 能力类型执行 API、navigate、skill、knowledge，并支持用户确认。

表结构：复用 Task、日志表。

后端任务：

- 实现 `PlanRunCoordinator`。
- 实现 `CliCommandExecutor` 接入 Plan。
- 实现 `SkillExecutor` 接入 Plan。
- 实现 `KnowledgeRagExecutor` 接入 Plan。
- 前序步骤结果写入 `ExecutionContextStore` 或 `resultRef`。
- 每步发送事件：开始、完成、失败。
- Planner 支持 `needConfirm`。
- Step 进入待确认状态时暂停 Run。
- 实现 `/chat/runs/confirm`：同意后继续，拒绝后取消，补充信息合并到 Task instruction。

前端任务：

- SDK 展示工具执行状态。
- SDK 展示确认卡片，支持同意、拒绝、补充说明。
- Run 详情展示每步输入、输出摘要、错误。

自测标准：

- 多步骤按顺序执行。
- 后续步骤能读取前序步骤结果。
- 需要确认的步骤不会自动执行。
- 同意后从该步骤继续，拒绝后 Run 取消。

## 阶段 9：ReAct Agent 编排

### 9.1 ReAct 执行器基础

目标：接入 Spring AI Alibaba ReactAgent。

表结构：复用 Run、消息、CLI、Skill、KB。

后端任务：

- 引入 `spring-ai-alibaba-agent-framework`。
- 实现 `ReactAgentRunner`。
- 构建 ReAct system prompt。
- 使用 conversationCode 作为 threadId。
- 接入 `ChatModel` 和模型角色解析。
- 输出最终回复并保存助手消息。
- 支持配置 `agent.orchestration-mode=react`。

前端任务：

- SDK 继续使用 Run events，不感知内部 plan/react 差异。

自测标准：

- 配置 `agent.orchestration-mode=react` 后 Run 能用 ReAct 执行。
- 普通问题不调用工具也能返回最终回答。

### 9.2 ReAct 工具注册与工具状态

目标：把平台工具、CLI、Skill、Knowledge、文件上传注册为 ReAct Tool，并向前端输出工具状态。

表结构：复用已有表。

后端任务：

- 实现 `PlatformDefaultToolsRegistry`、天气工具、节假日工具。
- 实现 `ReactCliToolFactory`。
- 实现 `ReactSkillToolFactory`。
- 实现 `ReactKnowledgeToolFactory`。
- 实现 `ReactGeneratedFileToolFactory`。
- 实现 `ReactThoughtModelInterceptor`。
- 实现 `ReactAgentStreamHandler`。
- 实现 `ReactToolStatusEmitter`。
- 事件包含 thought、tool_start、tool_success、tool_error、answer_token。

前端任务：

- SDK 展示“思考中”“正在查询”“已完成”等状态。
- 支持折叠/展开思考过程。

自测标准：

- 项目绑定的 CLI/Skill/KB 都能注册为工具。
- 工具数过多时使用能力检索限制注册范围。
- 调用 CLI 时前端能看到工具开始和完成。

### 9.3 ReAct 自动跳转、自动 API 建议与 Checkpoint

目标：完善 ReAct 自动化和记忆能力。

表结构：无新增，使用 Redis。

后端任务：

- 实现 `ReactSkillCliAutoInvoker`。
- 实现 `ReactSkillNavigateAutoInvoker`。
- 防止重复调用同一 navigate 工具。
- 实现 `ReactCheckpointRedisSaver`。
- 实现 `ReactCheckpointRedisRepository`。
- 设置 checkpoint TTL。
- Run 完成或失败后清理 `ExecutionContextStore`。

前端任务：

- SDK 展示自动跳转卡片。
- 宿主可监听 `innerLink`。

自测标准：

- Skill 输出建议调用时能自动调用 API。
- Skill 输出建议跳转时能生成跳转卡片。
- 同一会话多轮 ReAct 能利用 threadId 记忆。

## 阶段 10：附件与生成文件

### 10.1 附件上传

目标：用户可上传附件并在消息中引用。

表结构：

- 新建 `agent_attachment_info`：文件名、文件 URL、文件大小、文件类型、创建人、修改人、软删、时间字段。

后端任务：

- 实现 `/attachment/upload`。
- 上传文件到对象存储。
- 写入附件表。
- 返回附件 ID、文件名、URL、类型、大小。

前端任务：

- SDK 输入框支持上传附件。
- 消息发送时携带附件列表。
- 展示附件卡片。

自测标准：

- 上传文件后数据库有记录。
- 消息表 `attachments` 保存附件 JSON。

### 10.2 附件解析并注入 Prompt

目标：模型能理解用户上传文件内容。

表结构：复用附件表和消息表。

后端任务：

- 实现 `AttachmentFileLoader`。
- 实现 `AttachmentContextService`。
- 根据扩展名解析 txt、csv、xlsx、pdf、docx。
- 附件正文注入 `CreateRunDto.attachmentContext`。
- Prompt 中明确附件优先级，避免误调用实时 API。

前端任务：

- SDK 发送消息时传附件 ID。
- 消息列表展示附件解析状态，基础版可只展示已上传。

自测标准：

- 上传一个 CSV 后问“分析这个文件”，模型基于附件内容回答。
- 未上传附件时不注入附件上下文。

### 10.3 生成文件上传与文件卡片

目标：Agent/Skill 生成 HTML 或报告后上传成文件卡片。

表结构：复用 `agent_attachment_info`，消息内容中附加 workflow state。

后端任务：

- 实现 `GeneratedFileContentSupport`。
- 实现 `GeneratedFileUploadTools`。
- ReAct 中注册 `upload_generated_file`。
- 上传成功后把文件 artifact 写入 Run complete payload。

前端任务：

- SDK 支持展示生成文件卡片。
- 文件卡片可点击打开或下载。

自测标准：

- Skill 生成报告后能上传文件。
- 前端显示文件卡片。
- 会话历史中能恢复文件卡片。

## 阶段 11：管理后台完善

### 11.1 资源管理与能力选择器

目标：后台统一选择 CLI、Skill、KB、模型等资源。

表结构：无新增。

后端任务：

- 实现 `/admin/resource/list`。
- 实现 `/admin/resource/listByIds`。
- 返回资源类型、ID、名称、描述、状态。

前端任务：

- 实现通用资源选择组件。
- 项目绑定、Skill 资源绑定复用该组件。

自测标准：

- 能按资源类型查询。
- 已选资源回显正确。

### 11.2 缓存刷新

目标：配置变更后能刷新能力目录缓存。

表结构：无新增。

后端任务：

- 实现 `/admin/cache/refresh`。
- 支持刷新全部或指定模块：项目、CLI、Skill、KB、Schema。
- 刷新后清理 `CapabilityCatalogCache`。

前端任务：

- 后台增加“刷新缓存”按钮。
- CLI、Skill、项目绑定保存后提示刷新或自动刷新。

自测标准：

- 新增 CLI 后不重启服务，刷新缓存即可被 Agent 使用。

### 11.3 会话和 Run 管理增强

目标：后台能排查一次对话执行全过程。

表结构：复用 Run、Task、日志表。

后端任务：

- 会话详情返回消息、Run、Task、工具调用日志、检索日志。
- 支持按用户、项目、时间、状态筛选会话。

前端任务：

- 会话详情页增加 Run 时间线。
- 展示 Task 步骤、工具调用、检索命中、错误信息。

自测标准：

- 一次复杂 Agent 执行可在后台完整追踪。

## 阶段 12：安全、权限、观测与发布

### 12.1 用户身份与业务上下文

目标：从接入方 token 解析用户和业务上下文。

表结构：无新增。

后端任务：

- 实现 `OauthAspect` 或认证拦截器。
- 从 headers 读取 `x-access-titc-c-token`、`x-bloc-code`、`x-hotel-code`。
- 对接 OAuth/SSO/EHR 获取用户信息。
- 写入 `UserInfoThreadHolder`。

前端任务：

- SDK init/open 时传 token、blocCode、hotelCode。
- 每次请求自动带 headers。

自测标准：

- 后端能拿到用户 ID、集团、酒店。
- 无 token 时返回未授权或降级为游客策略。

### 12.2 页面资源权限校验

目标：页面跳转前校验用户是否有资源权限。

表结构：复用 `agent_page_definition.resource_project/resource_ids`。

后端任务：

- 完善 `ResourcePermissionValidator`。
- 对接外部权限系统。
- 无权限时返回 permissionDenied 跳转结果，不生成跳转卡片。

前端任务：

- SDK 展示无权限提示。

自测标准：

- 无权限页面不会被打开。
- 有权限用户可正常跳转。

### 12.3 日志、Trace、错误处理

目标：问题可排查、错误可定位。

表结构：复用日志表。

后端任务：

- 全链路 traceId 注入 Run、日志和事件。
- 统一异常处理。
- 工具调用、检索、Run、Task 都记录关键日志。
- 敏感信息脱敏。

前端任务：

- 管理后台 Run 详情展示 traceId。

自测标准：

- 任一失败 Run 能通过 traceId 找到日志。
- 日志不打印明文 token。

### 12.4 发布前回归

目标：完成端到端验收。

表结构：无新增。

后端任务：

- 编译所有模块。
- 准备初始化 SQL 与迁移 SQL。
- 准备环境配置模板。
- 准备关键接口 smoke test。

前端任务：

- 管理后台构建。
- SDK 构建 ESM、UMD、d.ts。
- 准备接入示例页面。

自测标准：

- 后端编译通过。
- 管理后台构建通过。
- SDK 构建通过。
- 完成普通聊天、附件分析、API 查询、页面跳转、Skill 分析、RAG 问答、Plan 多步、ReAct 工具调用、生成文件、后台排查。

## 端到端验收场景清单

| 场景 | 必须覆盖 |
| --- | --- |
| 普通聊天 | 建项目、建会话、发送普通问题、流式返回、历史可查。 |
| API 查询 | 建 API 型 CLI、配置 Tool、绑定项目、Agent 调 API、后台有调用日志。 |
| 页面跳转 | 建 PAGE 型 CLI、绑定项目、SDK 展示跳转卡片并派发 `innerLink`。 |
| Skill 分析 | 建 PROMPT Skill、绑定项目、Agent 调 Skill 返回结构化分析。 |
| RAG 问答 | 建知识库、上传文档、解析切片、建索引、Agent 基于命中片段回答。 |
| 附件分析 | SDK 上传 CSV/XLSX，Agent 基于附件内容回答，不误跳转页面。 |
| Plan 多步 | 用户说“查询数据并打开详情”，生成 API + navigate 两步，前序结果传后续。 |
| ReAct 工具调用 | ReAct 自动选择 CLI、Skill、KB，前端展示思考、工具状态、最终回答。 |
| 生成文件 | Skill 生成报告，ReAct 调用上传工具，SDK 展示文件卡片。 |
| 后台排障 | 通过会话查看 Run、Task、工具日志、检索日志、traceId 和失败原因。 |
