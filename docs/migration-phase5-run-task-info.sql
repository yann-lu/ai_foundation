-- ============================================================
-- Phase 5: Run 任务记录 + 表改名
-- ============================================================

-- 1. 表改名: agent_run -> agent_run_info
RENAME TABLE agent_run TO agent_run_info;

-- 2. 新增: agent_run_task_info (工具调用/子任务记录表)
CREATE TABLE IF NOT EXISTS `agent_run_task_info` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `run_id` BIGINT NOT NULL COMMENT '关联的Run ID',
  `task_code` VARCHAR(64) DEFAULT NULL COMMENT '任务编码',
  `task_type` VARCHAR(32) DEFAULT NULL COMMENT '任务类型: tool/skill/knowledge',
  `capability_type` VARCHAR(32) DEFAULT NULL COMMENT '能力类型: API/PAGE',
  `ref_id` BIGINT DEFAULT NULL COMMENT '关联资源ID (CLI/Skill/Page ID)',
  `ref_name` VARCHAR(255) DEFAULT NULL COMMENT '关联资源名称',
  `instruction` TEXT DEFAULT NULL COMMENT '任务指令/用户原始请求',
  `input_params` TEXT DEFAULT NULL COMMENT '入参JSON',
  `result_ref` TEXT DEFAULT NULL COMMENT '结果摘要/引用',
  `task_state` VARCHAR(32) DEFAULT NULL COMMENT '任务状态: pending/running/completed/failed',
  `error_message` VARCHAR(1000) DEFAULT NULL COMMENT '失败原因',
  `cost_ms` BIGINT DEFAULT NULL COMMENT '耗时(毫秒)',
  `is_delete` TINYINT NOT NULL DEFAULT 0 COMMENT '软删: 0-未删 1-已删',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_run_id` (`run_id`),
  KEY `idx_task_state` (`task_state`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent Run 子任务/工具调用记录表';
