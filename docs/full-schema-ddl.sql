-- AI Foundation 从零初始化 DDL
-- 说明：本文件依据当前 dal PO 实体和 db 增量迁移脚本整理，用于新库初始化或开发建表参考。
-- 约定：不使用物理外键；关联关系通过业务字段维护；统一使用 utf8mb4；统一使用 is_delete 逻辑删除。

SET NAMES utf8mb4;

-- =========================
-- 1. 项目与模型
-- =========================

CREATE TABLE IF NOT EXISTS `agent_project` (
    `id`              BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `project_name`    VARCHAR(128)     NOT NULL DEFAULT '' COMMENT '项目名称',
    `project_code`    VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '项目编码，用于产品/SaaS隔离',
    `description`     VARCHAR(1024)    NOT NULL DEFAULT '' COMMENT '项目描述',
    `system_prompt`   MEDIUMTEXT       NULL COMMENT '项目固定系统提示词，支持变量占位符',
    `prompt_variables` JSON             NULL COMMENT '项目提示词变量定义JSON，描述变量名、是否必填、类型和默认值',
    `state`           TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：0-停用，1-启用',
    `create_user`     VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '创建人',
    `modify_user`     VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '修改人',
    `is_delete`       TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删：0-未删，1-已删',
    `create_time`     DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_project_code` (`project_code`, `is_delete`),
    KEY `idx_project_state` (`state`, `is_delete`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent项目表';

CREATE TABLE IF NOT EXISTS `agent_model_config` (
    `id`              BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `project_id`      BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '关联项目ID，agent_project.id',
    `model_name`      VARCHAR(128)     NOT NULL DEFAULT '' COMMENT '模型名称，如 deepseek-v4-pro、qwen-plus、bge-m3',
    `model_type`      VARCHAR(32)      NOT NULL DEFAULT '' COMMENT '模型类别：CHAT/EMBEDDING',
    `state`           TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：0-停用，1-启用',
    `create_user`     VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '创建人',
    `modify_user`     VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '修改人',
    `is_delete`       TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删：0-未删，1-已删',
    `create_time`     DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_project_model_type` (`project_id`, `model_type`, `state`, `is_delete`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型配置表';

-- =========================
-- 2. 会话、消息、Run、Task
-- =========================

CREATE TABLE IF NOT EXISTS `agent_conversation_info` (
    `id`                  BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `project_id`          BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '关联项目ID，agent_project.id',
    `product_code`        VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '产品编码，冗余字段，便于按产品查询',
    `conversation_code`   VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '会话对外编码',
    `user_id`             BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '用户ID',
    `context_variables`   JSON             NULL COMMENT '会话上下文变量JSON，创建会话时按项目变量定义固化',
    `title`               VARCHAR(256)     NOT NULL DEFAULT '' COMMENT '对话标题',
    `summary`             MEDIUMTEXT       NULL COMMENT '会话滚动摘要，用于压缩长期历史',
    `model_provider`      VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '模型提供商',
    `model_name`          VARCHAR(128)     NOT NULL DEFAULT '' COMMENT '模型名称',
    `is_pin`              TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否置顶：0-否，1-是',
    `last_message_time`   DATETIME         NULL COMMENT '最后消息时间',
    `state`               TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态：0-活跃，1-归档',
    `is_delete`           TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删：0-未删，1-已删',
    `create_time`         DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`         DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_conversation_code` (`conversation_code`, `is_delete`),
    KEY `idx_project_user` (`project_id`, `user_id`, `state`, `is_delete`),
    KEY `idx_product_context` (`product_code`, `is_delete`),
    KEY `idx_last_message_time` (`last_message_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话记录表';

CREATE TABLE IF NOT EXISTS `agent_message_info` (
    `id`                BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `conversation_id`   BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '关联会话ID，agent_conversation_info.id',
    `role`              VARCHAR(32)      NOT NULL DEFAULT '' COMMENT '消息角色：user/assistant/system/tool',
    `content`           MEDIUMTEXT       NULL COMMENT '消息文本内容',
    `token_count`       INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT '消息消耗Token数',
    `duration_ms`       INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT '本轮耗时，单位毫秒',
    `attachments`       MEDIUMTEXT       NULL COMMENT '消息附件信息，JSON数组字符串',
    `client_ip`         VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '客户端IP',
    `state`             TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：0-无效，1-有效',
    `is_delete`         TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删：0-未删，1-已删',
    `create_time`       DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_conversation_time` (`conversation_id`, `create_time`, `is_delete`),
    KEY `idx_conversation_role` (`conversation_id`, `role`, `is_delete`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='具体消息内容表';

CREATE TABLE IF NOT EXISTS `agent_run` (
    `id`                  BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `conversation_id`     BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '关联会话ID，agent_conversation_info.id',
    `run_code`            VARCHAR(64)      NOT NULL DEFAULT '' COMMENT 'Run对外编码',
    `trace_id`            VARCHAR(128)     NOT NULL DEFAULT '' COMMENT '全链路traceId',
    `product_code`        VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '产品编码',
    `message_id`          BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '触发Run的用户消息ID，agent_message_info.id',
    `run_type`            VARCHAR(32)      NOT NULL DEFAULT '' COMMENT 'Run类型：plan/react/chat',
    `task_state`          VARCHAR(32)      NOT NULL DEFAULT '' COMMENT '任务生命周期状态码，见AgentTaskStateEnum.code',
    `state`               TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '兼容状态：0-执行中，1-成功，2-失败',
    `tokens_prompt`       INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT 'Prompt token消耗',
    `tokens_completion`   INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT 'Completion token消耗',
    `cost`                DECIMAL(18, 6)   NOT NULL DEFAULT 0.000000 COMMENT '预估金额花费',
    `request_messages`    TEXT             NULL COMMENT '本次Run发给模型的完整消息栈JSON，供Inspector回放',
    `reply`               MEDIUMTEXT       NULL COMMENT '模型最终回复正文',
    `reasoning`           MEDIUMTEXT       NULL COMMENT '思考链内容',
    `is_delete`           TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删：0-未删，1-已删',
    `create_time`         DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`         DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_run_code` (`run_code`, `is_delete`),
    KEY `idx_conversation_run` (`conversation_id`, `create_time`, `is_delete`),
    KEY `idx_trace_id` (`trace_id`),
    KEY `idx_message_id` (`message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent总调度执行流水表';

CREATE TABLE IF NOT EXISTS `agent_task` (
    `id`                BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `run_id`            BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '关联Run ID，agent_run.id',
    `step_code`         VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '步骤编码，如step_1',
    `capability_type`   VARCHAR(32)      NOT NULL DEFAULT '' COMMENT '能力类型：api/navigate/skill/knowledge/analysis',
    `task_type`         VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '任务类型：API_CALL/RAG/PAGE_NAV/CLI_CALL/SKILL_EXEC',
    `ref_id`            BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '关联资源ID，根据capability_type指向CLI/Skill/KB等',
    `instruction`       MEDIUMTEXT       NULL COMMENT '子Agent或步骤具体指令',
    `depends_on`        VARCHAR(1024)    NOT NULL DEFAULT '' COMMENT '依赖步骤编码JSON数组',
    `step_state`        VARCHAR(32)      NOT NULL DEFAULT '' COMMENT '步骤生命周期状态码，见AgentStepStateEnum.code',
    `result_ref`        VARCHAR(1024)    NOT NULL DEFAULT '' COMMENT '中间产物引用，ExecutionContext key或摘要',
    `error_message`     VARCHAR(2048)    NOT NULL DEFAULT '' COMMENT '失败原因',
    `is_async`          TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否异步：0-同步，1-异步',
    `state`             TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '兼容状态：0-待办，1-进行中，2-成功，3-失败',
    `is_delete`         TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删：0-未删，1-已删',
    `create_time`       DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_run_step` (`run_id`, `step_code`, `is_delete`),
    KEY `idx_run_state` (`run_id`, `step_state`, `is_delete`),
    KEY `idx_ref` (`capability_type`, `ref_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主Agent派生的子任务表';

CREATE TABLE IF NOT EXISTS `agent_async_job` (
    `id`             BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `task_id`        BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '关联子任务ID，agent_task.id',
    `job_type`       VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '任务类型',
    `result`         MEDIUMTEXT       NULL COMMENT '异步结果返回内容',
    `state`          TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态：0-等待，1-进行，2-完成，3-异常',
    `is_delete`      TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删：0-未删，1-已删',
    `create_time`    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_task_id` (`task_id`, `is_delete`),
    KEY `idx_job_state` (`job_type`, `state`, `is_delete`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='异步任务执行记录表';

-- =========================
-- 3. CLI / API Tool / Page
-- =========================

CREATE TABLE IF NOT EXISTS `agent_cli_command` (
    `id`               BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `command_prefix`   VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '命令前缀/命名空间，如epms',
    `command_group`    VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '命令分组/模块，如order',
    `command_action`   VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '命令动作，如query',
    `command_name`     VARCHAR(128)     NOT NULL DEFAULT '' COMMENT '唯一英文标识，用于Agent工具名，如epms_cli_api_order_query',
    `cli_template`     VARCHAR(1024)    NOT NULL DEFAULT '' COMMENT '完整命令模板示例，供大模型参考',
    `description`      VARCHAR(2048)    NOT NULL DEFAULT '' COMMENT '命令功能描述，供大模型理解能力',
    `command_type`     VARCHAR(32)      NOT NULL DEFAULT '' COMMENT '底层类型：API-接口调用，PAGE-页面跳转，CUSTOM-自定义逻辑',
    `state`            TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：0-停用，1-启用',
    `create_user`      VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '创建人',
    `modify_user`      VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '修改人',
    `is_delete`        TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删：0-未删，1-已删',
    `create_time`      DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_command_name` (`command_name`, `is_delete`),
    KEY `idx_command_group` (`command_prefix`, `command_group`, `state`, `is_delete`),
    KEY `idx_command_type` (`command_type`, `state`, `is_delete`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CLI命令定义表';

CREATE TABLE IF NOT EXISTS `agent_cli_param` (
    `id`                  BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `cli_id`              BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '关联CLI命令ID，agent_cli_command.id',
    `param_name`          VARCHAR(128)     NOT NULL DEFAULT '' COMMENT '参数变量名，如hotelCode',
    `param_flag`          VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '命令行标志，如--hotelCode或-h，位置参数可为空',
    `param_type`          VARCHAR(32)      NOT NULL DEFAULT '' COMMENT '参数类型：String/Number/Boolean/Array/Object等',
    `item_type`           VARCHAR(32)      NOT NULL DEFAULT '' COMMENT 'Array元素类型，仅paramType=Array时有效',
    `is_required`         TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否必填：0-否，1-是',
    `description`         VARCHAR(1024)    NOT NULL DEFAULT '' COMMENT '参数描述，供大模型阅读',
    `default_value`       VARCHAR(1024)    NOT NULL DEFAULT '' COMMENT '参数默认值',
    `sort_order`          INT              NOT NULL DEFAULT 0 COMMENT '参数排序，影响位置参数读取顺序',
    `parent_param_name`   VARCHAR(256)     NOT NULL DEFAULT '' COMMENT '父参数名，Object子字段关联的父参数完整路径',
    `is_delete`           TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删：0-未删，1-已删',
    `create_time`         DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`         DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_cli_param` (`cli_id`, `sort_order`, `is_delete`),
    KEY `idx_cli_param_name` (`cli_id`, `param_name`, `is_delete`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CLI命令参数表';

CREATE TABLE IF NOT EXISTS `agent_project_cli_rel` (
    `id`             BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `project_id`     BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '关联项目ID，agent_project.id',
    `cli_id`         BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '关联CLI命令ID，agent_cli_command.id',
    `state`          TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：0-停用，1-启用',
    `create_user`    VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '创建人',
    `modify_user`    VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '修改人',
    `is_delete`      TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删：0-未删，1-已删',
    `create_time`    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_project_cli` (`project_id`, `cli_id`, `is_delete`),
    KEY `idx_cli_id` (`cli_id`, `is_delete`),
    KEY `idx_project_state` (`project_id`, `state`, `is_delete`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目与CLI命令关联表';

CREATE TABLE IF NOT EXISTS `agent_tool_definition` (
    `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `cli_id`            BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '关联CLI命令ID，作为该CLI的底层API实现',
    `tool_name`         VARCHAR(128)    NOT NULL DEFAULT '' COMMENT '工具/接口名称，英文标识',
    `description`       VARCHAR(2048)   NOT NULL DEFAULT '' COMMENT '工具描述，供底层调用参考',
    `url`               VARCHAR(1024)   NOT NULL DEFAULT '' COMMENT '接口调用地址',
    `method`            VARCHAR(16)     NOT NULL DEFAULT '' COMMENT '请求方式：GET/POST等',
    `auth_type`         VARCHAR(32)     NOT NULL DEFAULT '' COMMENT '鉴权类型：NONE/HEADER等',
    `schema_code`       VARCHAR(64)     NOT NULL DEFAULT '' COMMENT '关联接口Schema编码',
    `request_schema`    MEDIUMTEXT      NULL COMMENT '请求JSON Schema',
    `response_schema`   MEDIUMTEXT      NULL COMMENT '响应JSON Schema',
    `is_delete`         TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删：0-未删，1-已删',
    `create_time`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_cli_id` (`cli_id`, `is_delete`),
    KEY `idx_schema_code` (`schema_code`, `is_delete`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='底层工具API定义表';

CREATE TABLE IF NOT EXISTS `agent_tool_param` (
    `id`             BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tool_id`        BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '关联工具ID，agent_tool_definition.id',
    `param_name`     VARCHAR(128)     NOT NULL DEFAULT '' COMMENT '参数名称',
    `param_type`     VARCHAR(32)      NOT NULL DEFAULT '' COMMENT '参数类型：String/Number/Boolean等',
    `is_required`    TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否必填：0-否，1-是',
    `description`    VARCHAR(1024)    NOT NULL DEFAULT '' COMMENT '参数描述，供大模型阅读',
    `create_user`    VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '创建人',
    `modify_user`    VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '修改人',
    `is_delete`      TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删：0-未删，1-已删',
    `create_time`    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tool_param` (`tool_id`, `is_delete`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='底层工具参数表';

CREATE TABLE IF NOT EXISTS `agent_tool_call_log` (
    `id`                 BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `task_id`            BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '关联子任务ID，agent_task.id',
    `cli_id`             BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '触发调用的CLI命令ID，agent_cli_command.id',
    `tool_id`            BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '实际调用的底层工具配置ID，agent_tool_definition.id',
    `request_params`     MEDIUMTEXT       NULL COMMENT '生成的请求参数JSON',
    `response_content`   MEDIUMTEXT       NULL COMMENT '接口真实响应',
    `cost_time_ms`       BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '耗时，单位毫秒',
    `state`              TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态：0-成功，1-失败',
    `is_delete`          TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删：0-未删，1-已删',
    `create_time`        DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`        DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_task_id` (`task_id`, `is_delete`),
    KEY `idx_cli_tool` (`cli_id`, `tool_id`, `is_delete`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工具接口调用日志表';

CREATE TABLE IF NOT EXISTS `agent_page_definition` (
    `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `cli_id`             BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '关联CLI命令ID，作为该CLI的底层跳转实现',
    `page_name`          VARCHAR(128)    NOT NULL DEFAULT '' COMMENT '页面名称',
    `page_prefix`        VARCHAR(128)    NOT NULL DEFAULT '' COMMENT '跳转前缀：scheme://、http://、https://等',
    `page_route`         VARCHAR(512)    NOT NULL DEFAULT '' COMMENT '页面路由或Path',
    `description`        VARCHAR(1024)   NOT NULL DEFAULT '' COMMENT '页面功能描述',
    `display_type`       VARCHAR(32)     NOT NULL DEFAULT 'PAGE' COMMENT '展示类型：PAGE-页面，MODAL-弹框',
    `target_type`        VARCHAR(32)     NOT NULL DEFAULT 'INTERNAL' COMMENT '目标类型：INTERNAL-站内，EXTERNAL-站外',
    `resource_project`   VARCHAR(64)     NOT NULL DEFAULT '' COMMENT '资源项目编码，用于权限校验',
    `resource_ids`       VARCHAR(1024)   NOT NULL DEFAULT '' COMMENT '资源ID列表，逗号分隔，用于权限校验',
    `is_delete`          TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删：0-未删，1-已删',
    `create_time`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_cli_id` (`cli_id`, `is_delete`),
    KEY `idx_resource` (`resource_project`, `is_delete`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='页面定义表';

CREATE TABLE IF NOT EXISTS `agent_page_param` (
    `id`             BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `page_id`        BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '关联页面ID，agent_page_definition.id',
    `param_name`     VARCHAR(128)     NOT NULL DEFAULT '' COMMENT '参数名',
    `param_type`     VARCHAR(32)      NOT NULL DEFAULT '' COMMENT '参数类型',
    `is_required`    TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否必填：0-否，1-是',
    `description`    VARCHAR(1024)    NOT NULL DEFAULT '' COMMENT '参数描述',
    `is_delete`      TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删：0-未删，1-已删',
    `create_time`    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_page_param` (`page_id`, `is_delete`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='页面参数表';

-- =========================
-- 4. OpenAPI Schema 与 CLI 批量生成
-- =========================

CREATE TABLE IF NOT EXISTS `agent_api_schema_config` (
    `id`                     BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `schema_code`            VARCHAR(64)      NOT NULL DEFAULT '' COMMENT 'Schema配置编码',
    `schema_name`            VARCHAR(128)     NOT NULL DEFAULT '' COMMENT 'Schema配置名称',
    `schema_url`             VARCHAR(1024)    NOT NULL DEFAULT '' COMMENT 'Schema链接地址',
    `schema_content`         MEDIUMTEXT       NULL COMMENT 'OpenAPI Schema JSON内容，上传方式',
    `schema_resource_path`   VARCHAR(512)     NOT NULL DEFAULT '' COMMENT 'Schema资源路径，classpath路径，如openapi/pms.json',
    `base_url`               VARCHAR(1024)    NOT NULL DEFAULT '' COMMENT '接口Base URL',
    `command_prefix`         VARCHAR(64)      NOT NULL DEFAULT '' COMMENT 'CLI命令前缀',
    `state`                  TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：0-停用，1-启用',
    `create_user`            VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '创建人',
    `modify_user`            VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '修改人',
    `is_delete`              TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删：0-未删，1-已删',
    `create_time`            DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`            DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_schema_code` (`schema_code`, `is_delete`),
    KEY `idx_schema_state` (`state`, `is_delete`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent接口Schema配置表';

CREATE TABLE IF NOT EXISTS `agent_cli_batch_job` (
    `id`               BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `job_code`         VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '任务编号',
    `project_id`       BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '目标项目ID，agent_project.id',
    `schema_id`        BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT 'API Schema ID，agent_api_schema_config.id',
    `schema_code`      VARCHAR(64)      NOT NULL DEFAULT '' COMMENT 'API Schema编码',
    `total_count`      INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT '提交条数',
    `finished_count`   INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT '已完成条数',
    `success_count`    INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT '新建成功数',
    `updated_count`    INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT '覆盖更新数',
    `fail_count`       INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT '失败数',
    `state`            VARCHAR(32)      NOT NULL DEFAULT '' COMMENT '任务状态，见CliBatchJobStateEnum.code',
    `error_message`    VARCHAR(2048)    NOT NULL DEFAULT '' COMMENT '批次级错误信息',
    `create_user`      VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '创建人',
    `modify_user`      VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '修改人',
    `is_delete`        TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删：0-未删，1-已删',
    `create_time`      DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_job_code` (`job_code`, `is_delete`),
    KEY `idx_project_schema` (`project_id`, `schema_id`, `is_delete`),
    KEY `idx_job_state` (`state`, `is_delete`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CLI批量生成任务表';

CREATE TABLE IF NOT EXISTS `agent_cli_batch_item` (
    `id`                     BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `job_id`                 BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '批次ID，agent_cli_batch_job.id',
    `method`                 VARCHAR(16)      NOT NULL DEFAULT '' COMMENT 'HTTP方法',
    `path`                   VARCHAR(1024)    NOT NULL DEFAULT '' COMMENT 'OpenAPI路径',
    `operation_id`           VARCHAR(256)     NOT NULL DEFAULT '' COMMENT 'OpenAPI operationId',
    `full_url`               VARCHAR(2048)    NOT NULL DEFAULT '' COMMENT '完整请求URL',
    `url_match_key`          VARCHAR(1024)    NOT NULL DEFAULT '' COMMENT 'method|normalizedUrl匹配键，用于识别重复接口',
    `preview_command_name`   VARCHAR(128)     NOT NULL DEFAULT '' COMMENT '预览commandName',
    `cli_id`                 BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '生成或更新后的CLI ID，agent_cli_command.id',
    `state`                  VARCHAR(32)      NOT NULL DEFAULT '' COMMENT '明细状态，见CliBatchItemStateEnum.code',
    `error_message`          VARCHAR(2048)    NOT NULL DEFAULT '' COMMENT '失败原因',
    `is_delete`              TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删：0-未删，1-已删',
    `create_time`            DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`            DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_job_id` (`job_id`, `is_delete`),
    KEY `idx_url_match_key` (`url_match_key`(191)),
    KEY `idx_cli_id` (`cli_id`, `is_delete`),
    KEY `idx_item_state` (`state`, `is_delete`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CLI批量生成明细表';

-- =========================
-- 5. Skill
-- =========================

CREATE TABLE IF NOT EXISTS `agent_skill_definition` (
    `id`              BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `skill_name`      VARCHAR(128)     NOT NULL DEFAULT '' COMMENT '技能名称，如生成月度分析报告',
    `skill_code`      VARCHAR(128)     NOT NULL DEFAULT '' COMMENT '技能编码，英文标识',
    `description`     VARCHAR(2048)    NOT NULL DEFAULT '' COMMENT '技能描述，供大模型或用户理解用途',
    `skill_type`      VARCHAR(32)      NOT NULL DEFAULT '' COMMENT '技能类型：PROMPT-提示词模板，WORKFLOW-工作流编排，MULTI_AGENT-多智能体',
    `system_prompt`   MEDIUMTEXT       NULL COMMENT '系统提示词/核心指令模板，支持变量占位符',
    `config_json`     VARCHAR(2048)    NOT NULL DEFAULT '' COMMENT '扩展配置JSON，如Temperature、TopP、输出格式约束等',
    `state`           TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：0-停用，1-启用',
    `create_user`     VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '创建人',
    `modify_user`     VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '修改人',
    `is_delete`       TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删：0-未删，1-已删',
    `create_time`     DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_skill_code` (`skill_code`, `is_delete`),
    KEY `idx_skill_type` (`skill_type`, `state`, `is_delete`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent技能定义表';

CREATE TABLE IF NOT EXISTS `agent_skill_resource` (
    `id`              BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `skill_id`        BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '关联技能ID，agent_skill_definition.id',
    `resource_type`   VARCHAR(32)      NOT NULL DEFAULT '' COMMENT '资源类型：CLI-命令，KB-知识库，MODEL-模型，SKILL-子技能',
    `resource_id`     BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '关联资源ID，根据resource_type指向对应资源表',
    `sort_order`      INT              NOT NULL DEFAULT 0 COMMENT '排序/执行优先级',
    `create_user`     VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '创建人',
    `modify_user`     VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '修改人',
    `is_delete`       TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删：0-未删，1-已删',
    `create_time`     DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_skill_resource` (`skill_id`, `resource_type`, `sort_order`, `is_delete`),
    KEY `idx_resource` (`resource_type`, `resource_id`, `is_delete`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='技能关联资源表';

CREATE TABLE IF NOT EXISTS `agent_project_skill_rel` (
    `id`             BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `project_id`     BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '关联项目ID，agent_project.id',
    `skill_id`       BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '关联技能ID，agent_skill_definition.id',
    `state`          TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：0-停用，1-启用',
    `create_user`    VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '创建人',
    `modify_user`    VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '修改人',
    `is_delete`      TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删：0-未删，1-已删',
    `create_time`    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_project_skill` (`project_id`, `skill_id`, `is_delete`),
    KEY `idx_skill_id` (`skill_id`, `is_delete`),
    KEY `idx_project_state` (`project_id`, `state`, `is_delete`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目与技能关联表';

-- =========================
-- 6. RAG 知识库
-- =========================

CREATE TABLE IF NOT EXISTS `agent_knowledge_base` (
    `id`                  BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `project_id`          BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '关联项目ID，agent_project.id',
    `kb_code`             VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '知识库编码，项目内唯一',
    `kb_name`             VARCHAR(128)     NOT NULL DEFAULT '' COMMENT '知识库名称',
    `description`         VARCHAR(1024)    NOT NULL DEFAULT '' COMMENT '知识库描述',
    `biz_domain`          VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '业务域标签',
    `tags`                VARCHAR(1024)    NOT NULL DEFAULT '' COMMENT '标签JSON字符串',
    `embedding_model`     VARCHAR(64)      NOT NULL DEFAULT '' COMMENT 'embedding模型，如bge-m3',
    `chunk_strategy`      VARCHAR(32)      NOT NULL DEFAULT 'TOKEN' COMMENT '分片策略：TOKEN/MARKDOWN/TABLE',
    `retrieval_config`    VARCHAR(2048)    NOT NULL DEFAULT '' COMMENT '检索配置JSON字符串',
    `router_summary`      VARCHAR(1024)    NOT NULL DEFAULT '' COMMENT 'KB Router摘要，用于多知识库选路',
    `doc_count`           INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT '文档数量',
    `chunk_count`         INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT '切片数量',
    `vector_collection`   VARCHAR(128)     NOT NULL DEFAULT '' COMMENT '向量库collection名称',
    `state`               TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：0-停用，1-启用',
    `create_user`         VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '创建人',
    `modify_user`         VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '修改人',
    `is_delete`           TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删：0-未删，1-已删',
    `create_time`         DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`         DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_project_kb_code` (`project_id`, `kb_code`, `is_delete`),
    KEY `idx_biz_domain` (`project_id`, `biz_domain`, `is_delete`),
    KEY `idx_project_state` (`project_id`, `state`, `is_delete`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库表';

CREATE TABLE IF NOT EXISTS `agent_knowledge_document` (
    `id`              BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `kb_id`           BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '关联知识库ID，agent_knowledge_base.id',
    `project_id`      BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '关联项目ID，agent_project.id',
    `doc_name`        VARCHAR(256)     NOT NULL DEFAULT '' COMMENT '文档名称',
    `doc_type`        VARCHAR(32)      NOT NULL DEFAULT '' COMMENT '文档类型/扩展名，如pdf/docx/xlsx/txt',
    `mime_type`       VARCHAR(128)     NOT NULL DEFAULT '' COMMENT 'MIME类型',
    `file_url`        VARCHAR(1024)    NOT NULL DEFAULT '' COMMENT '文件URL',
    `file_size`       BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '文件大小，单位字节',
    `file_md5`        VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '文件MD5',
    `source_type`     VARCHAR(32)      NOT NULL DEFAULT 'UPLOAD' COMMENT '来源：UPLOAD/URL/SYNC',
    `source_url`      VARCHAR(1024)    NOT NULL DEFAULT '' COMMENT '原始URL',
    `parse_state`     TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '解析状态：0-待处理，1-处理中，2-成功，3-失败',
    `parse_error`     VARCHAR(1024)    NOT NULL DEFAULT '' COMMENT '解析失败原因',
    `parse_version`   INT UNSIGNED     NOT NULL DEFAULT 1 COMMENT '解析版本',
    `ingest_job_id`   BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '异步入库任务ID',
    `doc_summary`     VARCHAR(1024)    NOT NULL DEFAULT '' COMMENT '文档摘要',
    `content_mode`    VARCHAR(32)      NOT NULL DEFAULT 'TEXT' COMMENT '内容模式：TEXT/TABLE/MIXED/SCAN',
    `char_count`      INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT '字符数',
    `chunk_count`     INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT '切片数',
    `asset_count`     INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT '衍生资源数',
    `metadata_json`   VARCHAR(2048)    NOT NULL DEFAULT '' COMMENT '扩展元数据JSON字符串',
    `state`           TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：0-无效，1-有效',
    `create_user`     VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '创建人',
    `modify_user`     VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '修改人',
    `is_delete`       TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删：0-未删，1-已删',
    `create_time`     DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_project_kb` (`project_id`, `kb_id`, `is_delete`),
    KEY `idx_parse_state` (`kb_id`, `parse_state`, `is_delete`),
    KEY `idx_file_md5` (`kb_id`, `file_md5`, `is_delete`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识文档表';

CREATE TABLE IF NOT EXISTS `agent_knowledge_chunk` (
    `id`                BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `doc_id`            BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '关联文档ID，agent_knowledge_document.id',
    `kb_id`             BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '关联知识库ID，agent_knowledge_base.id',
    `project_id`        BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '关联项目ID，agent_project.id',
    `asset_id`          BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '衍生资源ID，agent_knowledge_document_asset.id',
    `chunk_index`       INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT '切片序号',
    `content_type`      VARCHAR(32)      NOT NULL DEFAULT 'TEXT' COMMENT '内容类型：TEXT/IMAGE/TABLE等',
    `content`           MEDIUMTEXT       NULL COMMENT '切片内容',
    `anchor_text`       VARCHAR(1024)    NOT NULL DEFAULT '' COMMENT '上下文锚文本',
    `token_size`        INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT '估算token数量',
    `char_count`        INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT '字符数',
    `page_no`           INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT '来源页码',
    `sheet_name`        VARCHAR(128)     NOT NULL DEFAULT '' COMMENT 'Excel Sheet名称',
    `row_start`         INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT '表格起始行',
    `row_end`           INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT '表格结束行',
    `vector_id`         VARCHAR(128)     NOT NULL DEFAULT '' COMMENT '向量ID',
    `embedding_model`   VARCHAR(64)      NOT NULL DEFAULT '' COMMENT 'embedding模型',
    `metadata_json`     VARCHAR(2048)    NOT NULL DEFAULT '' COMMENT '扩展元数据JSON字符串',
    `state`             TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：0-无效，1-有效',
    `is_delete`         TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删：0-未删，1-已删',
    `create_time`       DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_kb_doc` (`kb_id`, `doc_id`, `is_delete`),
    KEY `idx_project_kb` (`project_id`, `kb_id`, `is_delete`),
    KEY `idx_content_type` (`kb_id`, `content_type`, `is_delete`),
    KEY `idx_asset_id` (`asset_id`),
    KEY `idx_vector_id` (`vector_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识切片表';

CREATE TABLE IF NOT EXISTS `agent_knowledge_document_asset` (
    `id`               BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `doc_id`           BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '关联文档ID，agent_knowledge_document.id',
    `kb_id`            BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '关联知识库ID，agent_knowledge_base.id',
    `project_id`       BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '关联项目ID，agent_project.id',
    `asset_type`       VARCHAR(32)      NOT NULL DEFAULT '' COMMENT '资源类型：EMBEDDED_IMAGE/PAGE_SCAN/TABLE_SNAPSHOT',
    `asset_name`       VARCHAR(256)     NOT NULL DEFAULT '' COMMENT '资源名称',
    `file_url`         VARCHAR(1024)    NOT NULL DEFAULT '' COMMENT '资源文件URL',
    `mime_type`        VARCHAR(128)     NOT NULL DEFAULT '' COMMENT 'MIME类型',
    `file_size`        BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '文件大小，单位字节',
    `file_md5`         VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '文件MD5',
    `source_page_no`   INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT '来源页码',
    `source_index`     INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT '同页内序号',
    `width`            INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT '图片宽度',
    `height`           INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT '图片高度',
    `anchor_text`      VARCHAR(1024)    NOT NULL DEFAULT '' COMMENT '上下文锚文本',
    `ocr_text`         MEDIUMTEXT       NULL COMMENT 'OCR文本',
    `caption_text`     MEDIUMTEXT       NULL COMMENT 'Vision描述',
    `process_state`    TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '处理状态：0-待处理，1-处理中，2-成功，3-失败',
    `process_error`    VARCHAR(1024)    NOT NULL DEFAULT '' COMMENT '处理失败原因',
    `chunk_id`         BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '关联切片ID，agent_knowledge_chunk.id',
    `metadata_json`    VARCHAR(2048)    NOT NULL DEFAULT '' COMMENT '扩展元数据JSON字符串',
    `state`            TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：0-无效，1-有效',
    `is_delete`        TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删：0-未删，1-已删',
    `create_time`      DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_doc_state` (`doc_id`, `process_state`, `is_delete`),
    KEY `idx_kb_doc` (`kb_id`, `doc_id`, `is_delete`),
    KEY `idx_chunk_id` (`chunk_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识文档衍生资源表';

CREATE TABLE IF NOT EXISTS `agent_project_kb_rel` (
    `id`             BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `project_id`     BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '项目ID，agent_project.id',
    `kb_id`          BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '知识库ID，agent_knowledge_base.id',
    `bind_type`      VARCHAR(32)      NOT NULL DEFAULT 'DEFAULT' COMMENT '绑定类型：DEFAULT/AGENT/SKILL',
    `ref_id`         BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '关联ID，bind_type为AGENT/SKILL时指向对应资源',
    `priority`       INT              NOT NULL DEFAULT 0 COMMENT '优先级，数值越大优先级越高',
    `is_default`     TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否默认知识库：0-否，1-是',
    `state`          TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：0-停用，1-启用',
    `create_user`    VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '创建人',
    `modify_user`    VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '修改人',
    `is_delete`      TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删：0-未删，1-已删',
    `create_time`    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_project_kb_bind` (`project_id`, `kb_id`, `bind_type`, `ref_id`, `is_delete`),
    KEY `idx_project_bind` (`project_id`, `bind_type`, `state`, `is_delete`),
    KEY `idx_kb_id` (`kb_id`, `is_delete`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目知识库绑定表';

CREATE TABLE IF NOT EXISTS `agent_retrieval_log` (
    `id`                  BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `task_id`             BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '关联子任务ID，agent_task.id',
    `run_id`              BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT 'Run ID，agent_run.id',
    `conversation_id`     BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '会话ID，agent_conversation_info.id',
    `project_id`          BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '项目ID，agent_project.id',
    `kb_id`               BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '查询的知识库ID，agent_knowledge_base.id',
    `kb_ids`              VARCHAR(1024)    NOT NULL DEFAULT '' COMMENT 'Router候选或选中知识库ID列表JSON字符串',
    `query_text`          VARCHAR(2048)    NOT NULL DEFAULT '' COMMENT '检索原词',
    `rewritten_query`     VARCHAR(2048)    NOT NULL DEFAULT '' COMMENT '改写后的query',
    `filter_expression`   VARCHAR(1024)    NOT NULL DEFAULT '' COMMENT 'filter表达式',
    `retrieved_chunks`    MEDIUMTEXT       NULL COMMENT '召回内容JSON快照',
    `recall_count`        INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT '召回数量',
    `rerank_count`        INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT '重排后数量',
    `cost_time_ms`        BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '耗时，单位毫秒',
    `state`               TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态：0-成功，1-失败',
    `error_message`       VARCHAR(1024)    NOT NULL DEFAULT '' COMMENT '失败原因',
    `is_delete`           TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删：0-未删，1-已删',
    `create_time`         DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`         DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_run_id` (`run_id`, `is_delete`),
    KEY `idx_task_id` (`task_id`, `is_delete`),
    KEY `idx_conversation` (`conversation_id`, `create_time`, `is_delete`),
    KEY `idx_project_kb` (`project_id`, `kb_id`, `is_delete`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库检索日志表';

-- =========================
-- 7. 附件
-- =========================

CREATE TABLE IF NOT EXISTS `agent_attachment_info` (
    `id`             BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `file_name`      VARCHAR(256)     NOT NULL DEFAULT '' COMMENT '文件名',
    `file_url`       VARCHAR(1024)    NOT NULL DEFAULT '' COMMENT '文件路径或URL',
    `size`           BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '文件大小，单位字节',
    `type`           VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '文件类型/扩展名',
    `create_user`    VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '创建人',
    `modify_user`    VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '修改人',
    `is_delete`      TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删：0-未删，1-已删',
    `create_time`    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_create_user` (`create_user`, `is_delete`),
    KEY `idx_file_name` (`file_name`(191))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='附件记录表';
