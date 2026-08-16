-- 新增 api_param_name 字段：CLI 参数对应的实际 API 参数名
-- 为空时 fallback 到 param_name
ALTER TABLE agent_cli_param ADD COLUMN api_param_name VARCHAR(128) DEFAULT NULL COMMENT '实际API参数名，为空则用param_name' AFTER param_name;
