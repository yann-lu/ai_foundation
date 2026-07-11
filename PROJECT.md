# AI Foundation 项目文档

## 项目概述

AI Foundation 是一个 Agent 编排平台，按项目空间管理 Chat/Embedding 模型、CLI、Skill、知识库等能力，对外提供会话、消息、Agent 编排、RAG 检索等能力，并配套管理后台与 SDK。

当前进度：**阶段 0（工程骨架与基础设施）**、**阶段 1（管理后台基础与项目配置）**、**阶段 2（会话、消息与最小 Chat 闭环）** 均已完成并通过端到端联调。

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
- **模型配置管理**：`agent_model_config` 表；分页/详情/新增/修改/删除；按项目筛选；模型类型 CHAT/EMBEDDING。
- **AgentModelResolver** 雏形：按项目读取启用模型，未配置时回退系统默认（`agent.ai.models`）。
- **管理后台前端**：Vue3 + Element Plus；登录页（token 存 localStorage）；请求拦截器自动携带 `x-admin-token`；可折叠侧边栏布局；项目配置页（列表/搜索/分页/增删改弹窗/状态标签）；模型配置页（按项目筛选/增删改）。


### 阶段 2
- **会话与消息表**：`agent_conversation_info`（会话编码、项目/产品上下文、模型信息、置顶、状态）+ `agent_message_info`（角色、内容、token 数、耗时、附件、客户端 IP）。
- **会话管理**：`AgentConversationMedService` 实现创建会话（按 productCode 查项目）、分页列表、详情（含消息）、删除（级联软删消息）、清空消息。`AgentMessageMedService` 实现最近消息、滚动消息。
- **会话编码生成**：`ConversationCodeGenerator`（`conv_` + 时间戳 + 随机串）。
- **同步 Chat**：`AiChatMedService.syncChat`，使用 Spring AI ChatClient 调用 OpenAI 兼容接口；支持 `modelName`、`systemPrompt`、`temperature`、`maxTokens`；加载历史消息构建上下文；保存 user/assistant 消息。
- **流式 Chat（SSE）**：`AiChatMedService.streamChat`，返回 `Flux<ServerSentEvent>`；事件类型 start/token/complete/error；流式完成后保存完整 assistant 消息。
- **ChatClient 手动配置**：`AiClientConfig` 手动创建 `OpenAiApi` → `OpenAiChatModel` → `ChatClient` Bean（`@ConditionalOnMissingBean` 不覆盖自动配置）。
- **管理后台前端**：新增「会话管理」页（分页/详情/清空/删除）+「Playground」页（选择项目→创建会话→流式对话，SSE 逐 token 展示）。

## 接口清单

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

### 访问地址
- 后端：http://localhost:8080（健康检查 `GET /actuator/health` 返回 `{"status":"UP"}`）
- 前端：http://localhost:5173（浏览器访问，用 `admin` / `admin123` 登录；Vite 已代理 `/admin` → `:8080`）
- MySQL：localhost:3306，库名 `ai_foundation`，用户 `ai_app` / `AiApp@2026`
- Redis：localhost:6379
- 模型服务：Ollama `http://localhost:11434`（OpenAI 兼容，当前使用 `deepseek-r1:1.5b`）

## 后续阶段

阶段 3 起将实现：Run/事件流/Agent 生命周期、CLI 工具、Skill、知识库 RAG、Plan/ReAct 编排、SDK、文件附件等（详见 `docs/implementation-roadmap.md`）。
