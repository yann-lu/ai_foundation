# AI Foundation

Agent 编排平台 — 后端（Spring Boot 3 + WebFlux + Spring AI Alibaba）+ 管理后台（Vue3 + Element Plus）。

## 快速开始

### 1. 环境要求
- Java 17（Corretto/Zulu 17）
- Maven 3.6+
- Node 20+ / npm
- MySQL 8+/9.x、Redis

### 2. 启动中间件
```bash
brew services start mysql
brew services start redis
ollama serve   # 本地模型服务（OpenAI 兼容，默认端口 11434）
```
建库建表：执行 `docs/full-schema-ddl.sql` 中 `agent_project`、`agent_model_config`、`agent_conversation_info`、`agent_message_info` 四张表（或全量）。库名 `ai_foundation`，用户 `ai_app` / `AiApp@2026`。

拉取模型：`ollama pull deepseek-r1:1.5b`

### 3. 启动后端
```bash
cd backend
export JAVA_HOME=<你的 Java17 路径>
mvn -Pqa -DskipTests install
mvn -pl ai-foundation-gateway spring-boot:run -Pqa
```
验证：`curl http://localhost:8080/actuator/health` → `{"status":"UP"}`

### 4. 启动前端
```bash
cd frontend
npm install
npm run dev
```
浏览器访问 http://localhost:5173 ，账号 `admin` / `admin123`。

### 5. 访问地址
- 后端：http://localhost:8080（健康检查 UP）
- 前端：http://localhost:5173（浏览器访问，用 `admin` / `admin123` 登录）
- 中间件：MySQL 9.x（localhost:3306，库 `ai_foundation`，用户 `ai_app` / `AiApp@2026`）、Redis 8.x（localhost:6379）
- 模型服务：Ollama（localhost:11434，当前使用 `deepseek-r1:1.5b`）

### 功能页面
- 项目配置、模型配置、会话管理（管理后台菜单）
- Playground：对话后台测试台，支持项目/历史会话切换、System Prompt 临时覆盖、Run SSE 事件时间线、逐 token 流式输出，以及解析 `<think>...</think>` 思考链路


## 目录结构
- `backend/` — Maven 多模块后端（com/dal/facade/biz/mediator/gateway）
- `frontend/` — Vue3 + Element Plus 管理后台
- `docs/` — 路线图与 DDL

详见 `PROJECT.md`。
