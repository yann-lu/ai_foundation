-- =========================
-- Phase 3: Run、事件流与 Agent 生命周期
-- =========================

CREATE TABLE IF NOT EXISTS `agent_run` (
    `id`                  BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `conversation_id`     BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '关联会话ID，agent_conversation_info.id',
    `run_code`            VARCHAR(64)      NOT NULL DEFAULT '' COMMENT 'Run对外编码',
    `trace_id`            VARCHAR(128)     NOT NULL DEFAULT '' COMMENT '全链路traceId',
    `product_code`        VARCHAR(64)      NOT NULL DEFAULT '' COMMENT '产品编码',
    `message_id`          BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '触发Run的用户消息ID，agent_message_info.id',
    `run_type`            VARCHAR(32)      NOT NULL DEFAULT '' COMMENT 'Run类型：chat/plan/react',
    `task_state`          VARCHAR(32)      NOT NULL DEFAULT '' COMMENT '任务生命周期状态码：created/executing/completed/failed/cancelled',
    `state`               TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '兼容状态：0-执行中，1-成功，2-失败',
    `tokens_prompt`       INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT 'Prompt token消耗',
    `tokens_completion`   INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT 'Completion token消耗',
    `cost`                DECIMAL(18, 6)   NOT NULL DEFAULT 0.000000 COMMENT '预估金额花费',
    `is_delete`           TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '软删：0-未删，1-已删',
    `create_time`         DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`         DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_run_code` (`run_code`, `is_delete`),
    KEY `idx_conversation_run` (`conversation_id`, `create_time`, `is_delete`),
    KEY `idx_trace_id` (`trace_id`),
    KEY `idx_message_id` (`message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent总调度执行流水表';
