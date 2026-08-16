## 计划：创建 `db-direct` Skill

### 目标
创建一个通用的数据库直连 skill，放在 `~/.agents/skills/db-direct/` 下，Cursor/Codex/ZCode 都能自动发现和加载。

### 核心设计

**配置文件 `connections.json`**：
- 每个连接有 `id`、`label`、`host`、`port`、`user`、`password`、`database`、`readonly` 标记
- `workspaceHints` 数组用于按当前工作区路径自动匹配连接
- `readonly: true` 的连接只允许 SELECT，其他允许读写
- 若文件不存在或为空，skill 自动跳过

**执行脚本 `scripts/db_query.py`**：
- 用 Python `mysql-connector` 或 `pymysql` 连接数据库（优先试 import，没装则给出安装提示）
- 子命令：`--list`（列出连接）、`--test <id>`（测试连通性）、`--query <id> <sql>`（执行 SQL）、`--tables <id>`（列出表）
- 敏感操作（INSERT/UPDATE/DELETE）对 readonly 连接直接拒绝
- 结果以表格形式输出到 stdout

**SKILL.md**：
- 描述触发条件（查库、验数、执行 SQL 等）
- 指导 agent 先读 connections.json → 自动匹配连接 → 执行 SQL
- 无配置时跳过并提示用户配置

### 文件清单

```
~/.agents/skills/db-direct/
├── SKILL.md                    # skill 主文件
├── connections.example.json    # 连接配置模板
├── connections.json            # 实际连接配置（预填 ai_foundation）
└── scripts/
    └── db_query.py             # 数据库查询脚本
```

### 预填连接
- `ai-foundation-local`：localhost:3306/ai_foundation，用户 ai_app，可读写
- 保留 `example-qa` 模板供参考

### 验证
1. 脚本 `--test ai-foundation-local` 测试连通
2. 脚本 `--query ai-foundation-local "SELECT 1"` 验证查询
3. 确认 readonly 连接执行写入时被拒绝