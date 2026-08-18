# AI Foundation Backend

Spring Boot 3 + WebFlux + Spring AI Alibaba 的 Agent 编排平台后端。

## 模块结构

Maven 多模块工程，根 POM 在 `backend/pom.xml`：

- `ai-foundation-com` — 公共工具、常量、异常
- `ai-foundation-dal` — MyBatis-Plus 数据访问层、实体、Mapper
- `ai-foundation-facade` — 对外 RPC / API 外观接口
- `ai-foundation-biz` — 业务实现（项目、模型、会话、Run、Skill、MCP）
- `ai-foundation-mediator` — Agent 编排（React Agent、MCP 工具桥接、Prompt 组装）
- `ai-foundation-gateway` — Spring Boot 启动入口，REST/WebFlux 网关

## 环境要求

- Java 17（Corretto / Zulu 17）
- Maven 3.6+
- MySQL 8+ / 9.x、Redis
- 模型服务（Ollama OpenAI 兼容或云模型）

## 启动

```bash
cd backend
mvn -Pqa -DskipTests install
mvn -pl ai-foundation-gateway spring-boot:run -Pqa
```

健康检查：`curl http://localhost:8080/actuator/health` → `{"status":"UP"}`

## 数据库

库名 `ai_foundation`，用户 `ai_app`。DDL 见各服务本地维护的迁移脚本（`docs/` 目录不进入本仓库）。
