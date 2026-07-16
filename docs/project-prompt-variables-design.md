# Project 提示词变量设计

## 背景

平台需要在 Project 维度配置固定系统提示词，同时支持不同业务项目携带不同上下文变量。酒店类项目可能需要 `blocCode`、`hotelCode`，其他项目可能需要 `storeCode`、`memberId`、`tenantId` 等。固定在会话表中增加行业字段会限制平台通用性，因此改为 Project 定义变量、Conversation 固化变量值。

## 涉及表

### agent_project

新增字段：

```sql
ALTER TABLE `agent_project`
    ADD COLUMN `system_prompt` MEDIUMTEXT NULL COMMENT '项目固定系统提示词，支持变量占位符' AFTER `description`,
    ADD COLUMN `prompt_variables` JSON NULL COMMENT '项目提示词变量定义JSON，描述变量名、是否必填、类型和默认值' AFTER `system_prompt`;
```

字段说明：

| 字段 | 说明 |
| --- | --- |
| `system_prompt` | Project 固定系统提示词。每次对话都会加载并拼入 system prompt。支持 `{{变量名}}` 占位符。 |
| `prompt_variables` | Project 变量定义 JSON。用于前端渲染创建会话表单、后端创建会话时校验变量。 |

`prompt_variables` 推荐格式：

```json
[
  {
    "name": "blocCode",
    "label": "集团编码",
    "type": "string",
    "required": true,
    "description": "当前租户集团编码"
  },
  {
    "name": "hotelCode",
    "label": "酒店编码",
    "type": "string",
    "required": true,
    "description": "当前酒店编码"
  }
]
```

当前后端支持的 `type` 为 `string`、`number`、`boolean`。未配置或未知类型暂不做类型拦截。

### agent_conversation_info

新增字段并删除酒店业务专用字段：

```sql
ALTER TABLE `agent_conversation_info`
    ADD COLUMN `context_variables` JSON NULL COMMENT '会话上下文变量JSON，创建会话时按项目变量定义固化' AFTER `user_id`;

UPDATE `agent_conversation_info`
SET `context_variables` = JSON_OBJECT(
        'blocCode', `bloc_code`,
        'hotelCode', `hotel_code`
    )
WHERE (`bloc_code` IS NOT NULL AND `bloc_code` <> '')
   OR (`hotel_code` IS NOT NULL AND `hotel_code` <> '');

ALTER TABLE `agent_conversation_info`
    DROP INDEX `idx_product_context`,
    DROP COLUMN `bloc_code`,
    DROP COLUMN `hotel_code`,
    ADD KEY `idx_product_context` (`product_code`, `is_delete`);
```

字段说明：

| 字段 | 说明 |
| --- | --- |
| `context_variables` | 会话创建时传入并通过 Project 变量定义校验后的变量值快照。后续对话只需要传 `conversationCode`，不需要每次重复传变量。 |

示例：

```json
{
  "blocCode": "4012071",
  "hotelCode": "8062076"
}
```

## 什么时候传值

变量在创建会话时传入，接口为 `/chat/create`。

推荐请求：

```json
{
  "productCode": "pms",
  "title": "测试会话",
  "contextVariables": {
    "blocCode": "4012071",
    "hotelCode": "8062076"
  }
}
```

兼容旧请求字段：

```json
{
  "productCode": "pms",
  "blocCode": "4012071",
  "hotelCode": "8062076"
}
```

后端会把旧字段合并进 `contextVariables`，最终只写入 `agent_conversation_info.context_variables`。

## 什么时候写入

创建会话流程：

1. `/chat/create` 接收 `productCode`、`contextVariables`、标题、模型等参数。
2. 后端按 `productCode` 查询 `agent_project`。
3. 解析 `agent_project.prompt_variables`。
4. 合并请求中的 `contextVariables` 与兼容字段 `blocCode`、`hotelCode`。
5. 应用变量定义中的 `defaultValue`。
6. 校验 `required=true` 的变量是否有值。
7. 扫描 `system_prompt` 中的 `{{变量名}}` 占位符，若引用变量缺失则拦截。
8. 校验通过后将变量快照序列化写入 `agent_conversation_info.context_variables`。
9. 保存会话。

如果创建会话时没有传变量，而 Project 变量定义或系统提示词要求该变量，直接创建失败，不进入后续对话。

## 对话时如何使用

后续 `/chat/sync`、`/chat/stream`、`/chat/runs/create` 只需要传 `conversationCode` 与用户消息。

模型调用前的 system prompt 组装顺序：

1. 平台默认系统提示词。
2. Project 固定系统提示词 `agent_project.system_prompt`。
3. 会话变量统一格式化出的 `【系统上下文】` 块。
4. 会话摘要。
5. 本次请求临时 `systemPrompt`，主要用于 Playground 调试。

Project 固定系统提示词中的 `{{变量名}}` 会使用 `agent_conversation_info.context_variables` 替换。例如：

```text
当前集团编码：{{blocCode}}
当前酒店编码：{{hotelCode}}
```

会渲染为：

```text
当前集团编码：4012071
当前酒店编码：8062076
```

同时平台会统一追加上下文块：

```text
【系统上下文】
- blocCode：4012071
- hotelCode：8062076
请结合以上上下文理解用户诉求；涉及租户、权限或业务范围时以上下文为准。
```

## 设计约束

- Project 提示词负责业务规则，不建议写死具体集团、酒店、门店等变量值。
- 变量值以创建会话时的 `contextVariables` 为准，并在会话维度固化。
- 后续对话不重复接收变量，避免同一个会话内上下文漂移。
- `contextVariables` 中不要存储敏感明文，如证件号、手机号、密钥等。
- `prompt_variables` 是管理配置，必须是 JSON 数组；`context_variables` 是运行快照，必须是 JSON 对象。
