-- =========================================
-- Skill 技能管理相关表
-- =========================================

CREATE TABLE IF NOT EXISTS `agent_skill_definition` (
    `id`              BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `skill_name`      VARCHAR(128)     NOT NULL DEFAULT '' COMMENT '技能名称',
    `skill_code`      VARCHAR(128)     NOT NULL DEFAULT '' COMMENT '技能编码，英文标识',
    `description`     VARCHAR(2048)    NOT NULL DEFAULT '' COMMENT '技能描述，供大模型或用户理解用途',
    `skill_type`      VARCHAR(32)      NOT NULL DEFAULT 'PROMPT' COMMENT '技能类型：PROMPT-提示词模板，WORKFLOW-工作流编排',
    `system_prompt`   MEDIUMTEXT       NULL COMMENT '系统提示词/核心指令模板',
    `config_json`     VARCHAR(2048)    NOT NULL DEFAULT '{}' COMMENT '扩展配置JSON',
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
    `skill_id`        BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '关联技能ID',
    `resource_type`   VARCHAR(32)      NOT NULL DEFAULT '' COMMENT '资源类型：CLI-命令，KB-知识库，MODEL-模型，SKILL-子技能',
    `resource_id`     BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '关联资源ID',
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
    `project_id`     BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '关联项目ID',
    `skill_id`       BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '关联技能ID',
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
