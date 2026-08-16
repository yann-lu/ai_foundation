-- Phase 4: CLI 能力管理 DDL
-- 包含：CLI 命令、参数、工具定义、页面定义、项目绑定、召回标签

SET NAMES utf8mb4;

-- =========================
-- 3. CLI 能力管理
-- =========================

-- CLI 命令定义表
CREATE TABLE IF NOT EXISTS `agent_cli_command` (
    `id`               BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `command_prefix`   VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '命令前缀/命名空间，如 epms',
    `command_group`    VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '命令分组/模块，如 order',
    `command_action`   VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '命令动作，如 query',
    `command_name`     VARCHAR(128)     NOT NULL DEFAULT '' COMMENT '唯一英文标识，用于 Agent 工具名',
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

-- CLI 命令参数表
CREATE TABLE IF NOT EXISTS `agent_cli_param` (
    `id`                  BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `cli_id`              BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '关联CLI命令ID，agent_cli_command.id',
    `param_name`          VARCHAR(128)     NOT NULL DEFAULT '' COMMENT '参数变量名，如 hotelCode',
    `param_flag`          VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '命令行标志，如 --hotelCode 或 -h，位置参数可为空',
    `param_type`          VARCHAR(32)      NOT NULL DEFAULT '' COMMENT '参数类型：String/Number/Boolean/Array/Object 等',
    `item_type`           VARCHAR(32)      NOT NULL DEFAULT '' COMMENT 'Array 元素类型，仅 paramType=Array 时有效',
    `is_required`         TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否必填：0-否，1-是',
    `description`         VARCHAR(1024)    NOT NULL DEFAULT '' COMMENT '参数描述，供大模型阅读',
    `default_value`       VARCHAR(1024)    NOT NULL DEFAULT '' COMMENT '参数默认值',
    `sort_order`          INT              NOT NULL DEFAULT 0 COMMENT '参数排序，影响位置参数读取顺序',
    `parent_param_name`   VARCHAR(256)     NOT NULL DEFAULT '' COMMENT '父参数名（嵌套 Object 结构）',
    `is_delete`           TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删：0-未删，1-已删',
    `create_time`         DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`         DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_cli_id` (`cli_id`, `is_delete`),
    UNIQUE KEY `uk_cli_param_name` (`cli_id`, `param_name`, `is_delete`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CLI命令参数表';

-- 底层工具/API 定义表
CREATE TABLE IF NOT EXISTS `agent_tool_definition` (
    `id`                 BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `cli_id`             BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '关联CLI命令ID，agent_cli_command.id',
    `tool_name`          VARCHAR(128)     NOT NULL DEFAULT '' COMMENT '工具名称',
    `description`        VARCHAR(1024)    NOT NULL DEFAULT '' COMMENT '工具描述',
    `url`                VARCHAR(512)     NOT NULL DEFAULT '' COMMENT '接口调用地址',
    `method`             VARCHAR(16)      NOT NULL DEFAULT '' COMMENT '请求方法：GET/POST/PUT/DELETE',
    `auth_type`          VARCHAR(32)      NOT NULL DEFAULT '' COMMENT '鉴权类型：NONE/HEADER',
    `schema_code`        VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '关联 Schema 编码',
    `request_schema`     MEDIUMTEXT       NULL COMMENT '请求 JSON Schema',
    `response_schema`    MEDIUMTEXT       NULL COMMENT '响应 JSON Schema',
    `success_check_json` TEXT             NULL COMMENT '业务成功判定 JSON',
    `is_delete`          TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删：0-未删，1-已删',
    `create_time`        DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`        DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cli_id` (`cli_id`, `is_delete`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='底层工具/API定义表';

-- 页面定义表
CREATE TABLE IF NOT EXISTS `agent_page_definition` (
    `id`                 BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `cli_id`             BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '关联CLI命令ID，agent_cli_command.id',
    `page_name`          VARCHAR(128)     NOT NULL DEFAULT '' COMMENT '页面名称',
    `page_prefix`        VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '跳转前缀（scheme:// 或 http://）',
    `page_route`         VARCHAR(512)     NOT NULL DEFAULT '' COMMENT '页面路由/Path',
    `description`        VARCHAR(1024)    NOT NULL DEFAULT '' COMMENT '页面功能描述',
    `display_type`       VARCHAR(16)      NOT NULL DEFAULT '' COMMENT '展示类型：PAGE/MODAL',
    `target_type`        VARCHAR(16)      NOT NULL DEFAULT '' COMMENT '目标类型：INTERNAL-站内，EXTERNAL-站外',
    `resource_project`   VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '资源项目代码（OAuth 资源池）',
    `resource_ids`       VARCHAR(512)     NOT NULL DEFAULT '' COMMENT '资源 ID 列表，逗号分隔',
    `is_delete`          TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删：0-未删，1-已删',
    `create_time`        DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`        DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cli_id` (`cli_id`, `is_delete`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='页面定义表';

-- 页面参数表
CREATE TABLE IF NOT EXISTS `agent_page_param` (
    `id`            BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `page_id`       BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '关联页面ID，agent_page_definition.id',
    `param_name`    VARCHAR(128)     NOT NULL DEFAULT '' COMMENT '参数名',
    `param_type`    VARCHAR(32)      NOT NULL DEFAULT '' COMMENT '参数类型',
    `description`   VARCHAR(512)     NOT NULL DEFAULT '' COMMENT '参数描述',
    `is_required`   TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否必填：0-否，1-是',
    `default_value` VARCHAR(512)     NOT NULL DEFAULT '' COMMENT '默认值',
    `is_delete`     TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删：0-未删，1-已删',
    `create_time`   DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_page_id` (`page_id`, `is_delete`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='页面参数表';

-- 项目-CLI 绑定表
CREATE TABLE IF NOT EXISTS `agent_project_cli_mapping` (
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `project_id`   BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '项目ID，agent_project.id',
    `cli_id`       BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'CLI命令ID，agent_cli_command.id',
    `create_time`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_project_cli` (`project_id`, `cli_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目-CLI绑定表';

-- CLI 召回标签表
CREATE TABLE IF NOT EXISTS `agent_cli_recall_tag` (
    `id`          BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `cli_id`      BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '关联CLI ID，agent_cli_command.id',
    `tag_type`    VARCHAR(16)      NOT NULL DEFAULT '' COMMENT '标签类型：ALIAS/OP/DOMAIN/SLOT',
    `tag_value`   VARCHAR(256)     NOT NULL DEFAULT '' COMMENT '标签值',
    `weight`      INT              NOT NULL DEFAULT 0 COMMENT '命中加权',
    `match_mode`  VARCHAR(16)      NOT NULL DEFAULT '' COMMENT '匹配模式：exact-精确，contains-包含',
    `sort_order`  INT              NOT NULL DEFAULT 0 COMMENT '排序',
    `state`       TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：0-停用，1-启用',
    `is_delete`   TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删：0-未删，1-已删',
    `create_time` DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_cli_id` (`cli_id`, `is_delete`),
    KEY `idx_tag_type_value` (`tag_type`, `tag_value`, `is_delete`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CLI召回标签表';

-- API Schema 配置表（网关服务配置）
CREATE TABLE IF NOT EXISTS `agent_api_schema_config` (
    `id`              BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `schema_code`     VARCHAR(64)      NOT NULL DEFAULT '' COMMENT 'Schema配置编码',
    `schema_name`     VARCHAR(128)     NOT NULL DEFAULT '' COMMENT 'Schema配置名称',
    `base_url`        VARCHAR(512)     NOT NULL DEFAULT '' COMMENT '接口Base URL（网关地址）',
    `command_prefix`  VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '关联CLI命令前缀',
    `state`           TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：0-停用，1-启用',
    `create_user`     VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '创建人',
    `modify_user`     VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '修改人',
    `is_delete`       TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删：0-未删，1-已删',
    `create_time`     DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_schema_code` (`schema_code`, `is_delete`),
    KEY `idx_command_prefix` (`command_prefix`, `state`, `is_delete`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='API Schema配置表';
