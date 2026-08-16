-- Phase 5: MCP 服务器管理 DDL
-- MCP 服务器定义表，用于管理 MCP Server 实例（stdio 方式启动）
-- MCP 工具通过 MCP_SERVER_ID 关联，工具定义，作为 CLI 命令的一种底层类型 (command_type=MCP

SET NAMES utf8mb4;

-- MCP 服务器定义表
CREATE TABLE IF NOT EXISTS `agent_mcp_server` (
    `id`               BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `server_code`      VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '服务器编码，唯一标识',
    `server_name`      VARCHAR(128)     NOT NULL DEFAULT '' COMMENT '服务器名称',
    `description`      VARCHAR(1024)    NOT NULL DEFAULT '' COMMENT '描述',
    `transport_type`   VARCHAR(32)      NOT NULL DEFAULT 'stdio' COMMENT '传输方式：stdio/sse/http',
    `command`          VARCHAR(512)    NOT NULL DEFAULT '' COMMENT '启动命令（stdio 方式），如 node build/index.js',
    `working_dir`      VARCHAR(512)    NOT NULL DEFAULT '' COMMENT '工作目录',
    `env_vars`         TEXT             NULL COMMENT '环境变量 JSON，如 {"BING_URL":"https://cn.bing.com"}',
    `base_url`         VARCHAR(512)    NOT NULL DEFAULT '' COMMENT 'Base URL（sse/http 方式）',
    `auth_type`        VARCHAR(32)      NOT NULL DEFAULT '' COMMENT '鉴权类型：NONE/BEARER/BASIC',
    `auth_config`      TEXT             NULL COMMENT '鉴权配置 JSON',
    `state`            TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：0-停用，1-启用',
    `create_user`      VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '创建人',
    `modify_user`      VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '修改人',
    `is_delete`        TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删：0-未删，1-已删',
    `create_time`      DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_server_code` (`server_code`, `is_delete`),
    KEY `idx_state` (`state`, `is_delete`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP服务器定义表';

-- 为 agent_tool_definition 增加 mcp_server_id 和 mcp_tool_name 字段
ALTER TABLE `agent_tool_definition`
    ADD COLUMN `mcp_server_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '关联MCP服务器ID，agent_mcp_server.id' AFTER `cli_id`,
    ADD COLUMN `mcp_tool_name` VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'MCP工具名称' AFTER `tool_name`,
    ADD KEY `idx_mcp_server_id` (`mcp_server_id`, `is_delete`);
