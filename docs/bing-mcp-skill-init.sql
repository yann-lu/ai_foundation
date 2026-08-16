-- ============================================================
-- 必应中文搜索 MCP 能力 + Skill 初始化脚本
-- MCP Server: https://www.modelscope.cn/mcp/servers/rainerWJY/bing-cn-mcp-enhanced
-- 启动命令:  npx -y bing-cn-mcp-enhanced  （stdio，无环境变量）
-- 工具列表:
--   - bing_search(query, num_results)    必应中文搜索
--   - fetch_webpage(result_id)           抓取搜索结果对应网页正文
-- 默认挂载项目: travel_assistant (id=7)
-- ============================================================

SET NAMES utf8mb4;

-- ============================================================
-- 一、注册 MCP Server
-- ============================================================

INSERT INTO `agent_mcp_server`
(`server_code`, `server_name`, `description`, `transport_type`, `command`, `working_dir`, `env_vars`,
 `base_url`, `auth_type`, `auth_config`, `state`, `create_user`, `modify_user`)
VALUES
('bing_cn_enhanced', '必应中文搜索增强版',
 '基于 MCP 协议的中文必应搜索工具，无需 API Key，解决普通 Bing MCP 因反爬机制返回随机数据的问题。底层使用 playwright 渲染页面，首次启动会自动下载 Chromium。',
 'stdio', 'npx -y bing-cn-mcp-enhanced', '', NULL,
 '', 'NONE', NULL, 1, 'system', 'system');

SET @mcp_bing = LAST_INSERT_ID();


-- ============================================================
-- 二、CLI 命令配置（command_type=MCP）
-- ============================================================

-- ------------------------------------------------------------
-- CLI-001: 必应搜索 (bing search)
-- ------------------------------------------------------------
INSERT INTO `agent_cli_command`
(`command_prefix`, `command_group`, `command_action`, `command_name`, `cli_template`, `description`,
 `command_type`, `state`, `create_user`, `modify_user`)
VALUES
('bing', 'search', 'query', 'bing_search',
 'bing search query <query>(required) [num_results]',
 '使用必应中文搜索引擎检索网络信息，返回标题、链接、摘要。用户询问新闻、知识、攻略、景点介绍、餐厅评价、实时信息等需要联网查询的内容时使用。',
 'MCP', 1, 'system', 'system');

SET @cli_bing_search = LAST_INSERT_ID();

INSERT INTO `agent_cli_param` (`cli_id`, `param_name`, `param_flag`, `param_type`, `is_required`, `description`, `default_value`, `sort_order`)
VALUES
(@cli_bing_search, 'query', '<query>', 'String', 1, '搜索关键词，如"杭州西湖旅游攻略"、"北京今天天气"', '', 1),
(@cli_bing_search, 'num_results', '[num_results]', 'Number', 1, '返回结果数量，建议 5 条，最多不超过 10', '5', 2);

INSERT INTO `agent_tool_definition`
(`cli_id`, `mcp_server_id`, `tool_name`, `mcp_tool_name`, `description`,
 `url`, `method`, `auth_type`, `schema_code`, `request_schema`, `response_schema`)
VALUES
(@cli_bing_search, @mcp_bing, 'bing_search_tool', 'bing_search',
 '必应中文搜索',
 '', 'POST', 'NONE', '', NULL, NULL);


-- ------------------------------------------------------------
-- CLI-002: 抓取搜索结果网页 (bing fetch webpage)
-- ------------------------------------------------------------
INSERT INTO `agent_cli_command`
(`command_prefix`, `command_group`, `command_action`, `command_name`, `cli_template`, `description`,
 `command_type`, `state`, `create_user`, `modify_user`)
VALUES
('bing', 'webpage', 'fetch', 'bing_fetch_webpage',
 'bing webpage fetch <result_id>(required)',
 '根据 bing_search 返回的 result_id 抓取对应网页正文，自动剔除广告和导航。当搜索结果摘要不足以回答用户问题、需要看正文细节时使用。',
 'MCP', 1, 'system', 'system');

SET @cli_bing_fetch = LAST_INSERT_ID();

INSERT INTO `agent_cli_param` (`cli_id`, `param_name`, `param_flag`, `param_type`, `is_required`, `description`, `default_value`, `sort_order`)
VALUES
(@cli_bing_fetch, 'result_id', '<result_id>', 'String', 1, '从 bing_search 返回结果中取到的 id 字段，形如 result_xxx_1_xxx', '', 1);

INSERT INTO `agent_tool_definition`
(`cli_id`, `mcp_server_id`, `tool_name`, `mcp_tool_name`, `description`,
 `url`, `method`, `auth_type`, `schema_code`, `request_schema`, `response_schema`)
VALUES
(@cli_bing_fetch, @mcp_bing, 'bing_fetch_webpage_tool', 'fetch_webpage',
 '抓取必应搜索结果对应网页正文',
 '', 'POST', 'NONE', '', NULL, NULL);


-- ============================================================
-- 三、召回标签
-- ============================================================

INSERT INTO `agent_cli_recall_tag` (`cli_id`, `tag_type`, `tag_value`, `weight`, `match_mode`, `sort_order`, `state`) VALUES
(@cli_bing_search, 'ALIAS', '搜索', 10, 'contains', 1, 1),
(@cli_bing_search, 'ALIAS', '百度', 8, 'contains', 2, 1),
(@cli_bing_search, 'ALIAS', '必应', 10, 'contains', 3, 1),
(@cli_bing_search, 'ALIAS', '查一下', 8, 'contains', 4, 1),
(@cli_bing_search, 'ALIAS', '最新', 7, 'contains', 5, 1),
(@cli_bing_search, 'ALIAS', '新闻', 8, 'contains', 6, 1),
(@cli_bing_search, 'ALIAS', '什么是', 6, 'contains', 7, 1),
(@cli_bing_search, 'DOMAIN', '联网', 5, 'contains', 8, 1);

INSERT INTO `agent_cli_recall_tag` (`cli_id`, `tag_type`, `tag_value`, `weight`, `match_mode`, `sort_order`, `state`) VALUES
(@cli_bing_fetch, 'ALIAS', '网页正文', 8, 'contains', 1, 1),
(@cli_bing_fetch, 'ALIAS', '详细内容', 6, 'contains', 2, 1);


-- ============================================================
-- 四、Skill 定义 + 资源绑定 + 项目挂载
-- ============================================================

INSERT INTO `agent_skill_definition`
(`skill_name`, `skill_code`, `description`, `skill_type`, `system_prompt`, `config_json`, `state`,
 `create_user`, `modify_user`)
VALUES
('联网搜索', 'web_search',
 '联网搜索能力：当用户的问题涉及实时信息、新闻、知识查询、旅游攻略、餐厅/景点评价等需要外部数据的场景，调用 bing_search 检索；若摘要不足以回答，再用 bing_fetch_webpage 抓取正文。',
 'PROMPT',
 '你具备联网搜索能力。当用户的问题需要实时信息、新闻、外部知识、或你对答案不确定时，按以下步骤处理：
1. 调用 bing_search 工具搜索关键词，拿到 5 条结果（标题+摘要）。
2. 如果摘要已足够回答，直接基于摘要作答，并在回复末尾附上来源链接。
3. 如果摘要不够，从中挑选最相关的 1-2 条，调用 bing_fetch_webpage 抓取正文后再作答。
4. 不要凭空捏造搜索不到的信息；搜索不到时如实告知用户。',
 '{}', 1, 'system', 'system');

SET @skill_web_search = LAST_INSERT_ID();

INSERT INTO `agent_skill_resource` (`skill_id`, `resource_type`, `resource_id`, `sort_order`, `create_user`, `modify_user`)
VALUES
(@skill_web_search, 'CLI', @cli_bing_search, 0, 'system', 'system'),
(@skill_web_search, 'CLI', @cli_bing_fetch,  1, 'system', 'system');


-- ------------------------------------------------------------
-- 挂载到 travel_assistant 项目（id=7）
-- ------------------------------------------------------------
INSERT INTO `agent_project_skill_rel` (`project_id`, `skill_id`, `state`, `create_user`, `modify_user`)
VALUES (7, @skill_web_search, 1, 'system', 'system');


-- ============================================================
-- 数据统计
-- ============================================================
-- MCP Server:  1 个 (bing_cn_enhanced)
-- CLI 命令:    2 个（command_type=MCP）
--   - bing_search          必应搜索
--   - bing_fetch_webpage   抓取搜索结果网页
-- CLI 参数:    3 个
-- 召回标签:    10 个
-- Skill:       1 个 (web_search 联网搜索)
-- 项目挂载:    travel_assistant (id=7)
-- ============================================================
