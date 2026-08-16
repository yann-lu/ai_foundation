-- ============================================================
-- 旅游助手 Agent 演示数据初始化脚本
-- 包含：3 个网关服务 + 7 个 CLI 命令 + 1 个项目 + 项目模型配置 + 能力挂载
-- 使用方法：直接在数据库中执行即可
-- ============================================================

SET NAMES utf8mb4;

-- ============================================================
-- 一、网关服务配置 (agent_api_schema_config)
-- ============================================================

-- 1. Open-Meteo 天气服务（完全免费，无需 API Key）
INSERT INTO `agent_api_schema_config` (`schema_code`, `schema_name`, `schema_url`, `base_url`, `command_prefix`, `state`, `create_user`, `modify_user`)
VALUES ('open_meteo', 'Open-Meteo 天气服务', 'https://open-meteo.com/en/docs', 'https://api.open-meteo.com', 'weather', 1, 'system', 'system');

-- 2. REST Countries 国家信息服务（完全免费）
INSERT INTO `agent_api_schema_config` (`schema_code`, `schema_name`, `schema_url`, `base_url`, `command_prefix`, `state`, `create_user`, `modify_user`)
VALUES ('rest_countries', 'REST Countries 国家信息', 'https://restcountries.com', 'https://restcountries.com', 'country', 1, 'system', 'system');

-- 3. 汇率转换服务（免费额度）
INSERT INTO `agent_api_schema_config` (`schema_code`, `schema_name`, `schema_url`, `base_url`, `command_prefix`, `state`, `create_user`, `modify_user`)
VALUES ('exchange_rate', '汇率转换服务', 'https://exchangerate.host', 'https://api.exchangerate.host', 'exchange', 1, 'system', 'system');


-- ============================================================
-- 二、CLI 命令配置 (agent_cli_command + agent_cli_param + agent_tool_definition)
-- ============================================================

-- ------------------------------------------------------------
-- CLI-001: 地点经纬度搜索 (travel geo search)
-- ------------------------------------------------------------
INSERT INTO `agent_cli_command` (`command_prefix`, `command_group`, `command_action`, `command_name`, `cli_template`, `description`, `command_type`, `state`, `create_user`, `modify_user`)
VALUES ('travel', 'geo', 'search', 'travel_geo_search',
        'travel geo search <cityName>(required)',
        '根据城市名称搜索地点的经纬度、国家、时区等基础地理信息。输入城市中文名或英文名都可以。查询天气或地理位置相关问题时优先使用本工具。',
        'API', 1, 'system', 'system');

SET @cli_geo_search = LAST_INSERT_ID();

INSERT INTO `agent_cli_param` (`cli_id`, `param_name`, `param_flag`, `param_type`, `is_required`, `description`, `default_value`, `sort_order`)
VALUES
(@cli_geo_search, 'cityName', '<cityName>', 'String', 1, '城市名称，支持中文或英文，如"北京"、"Tokyo"、"Paris"', '', 1),
(@cli_geo_search, 'count', '[count]', 'Number', 0, '返回结果数量，默认5个', '5', 2),
(@cli_geo_search, 'language', '[language]', 'String', 0, '返回结果语言，默认zh（中文）', 'zh', 3);

-- 地理编码用单独的 API 域名，直接写完整 URL
INSERT INTO `agent_tool_definition` (`cli_id`, `tool_name`, `description`, `url`, `method`, `auth_type`, `schema_code`, `request_schema`, `response_schema`)
VALUES (@cli_geo_search, 'geo_search_tool',
        '地理编码搜索，根据城市名返回经纬度',
        'https://geocoding-api.open-meteo.com/v1/search',
        'GET', 'NONE', 'open_meteo',
        NULL, NULL);


-- ------------------------------------------------------------
-- CLI-002: 实时天气查询 (travel weather current)
-- ------------------------------------------------------------
INSERT INTO `agent_cli_command` (`command_prefix`, `command_group`, `command_action`, `command_name`, `cli_template`, `description`, `command_type`, `state`, `create_user`, `modify_user`)
VALUES ('travel', 'weather', 'current', 'travel_weather_current',
        'travel weather current <latitude>(required) <longitude>(required)',
        '根据经纬度查询指定地点的实时天气，包括温度、体感温度、湿度、风速、天气状况。用户询问当前/今天/实时天气时使用。纬度和经度需要通过 travel_geo_search 先获取。',
        'API', 1, 'system', 'system');

SET @cli_weather_current = LAST_INSERT_ID();

INSERT INTO `agent_cli_param` (`cli_id`, `param_name`, `param_flag`, `param_type`, `is_required`, `description`, `default_value`, `sort_order`)
VALUES
(@cli_weather_current, 'latitude', '<latitude>', 'Number', 1, '纬度，如 39.9042（北京）', '', 1),
(@cli_weather_current, 'longitude', '<longitude>', 'Number', 1, '经度，如 116.4074（北京）', '', 2);

INSERT INTO `agent_tool_definition` (`cli_id`, `tool_name`, `description`, `url`, `method`, `auth_type`, `schema_code`, `request_schema`, `response_schema`)
VALUES (@cli_weather_current, 'weather_current_tool',
        '实时天气查询',
        '/v1/forecast?current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m,weather_description&timezone=auto',
        'GET', 'NONE', 'open_meteo',
        NULL, NULL);


-- ------------------------------------------------------------
-- CLI-003: 7天天气预报 (travel weather forecast)
-- ------------------------------------------------------------
INSERT INTO `agent_cli_command` (`command_prefix`, `command_group`, `command_action`, `command_name`, `cli_template`, `description`, `command_type`, `state`, `create_user`, `modify_user`)
VALUES ('travel', 'weather', 'forecast', 'travel_weather_forecast',
        'travel weather forecast <latitude>(required) <longitude>(required) [days]',
        '查询指定地点未来多天的天气预报，包括每日最高/最低温度、降水概率、天气状况。用户询问未来/下周/天气预报/多少度/会不会下雨时使用。',
        'API', 1, 'system', 'system');

SET @cli_weather_forecast = LAST_INSERT_ID();

INSERT INTO `agent_cli_param` (`cli_id`, `param_name`, `param_flag`, `param_type`, `is_required`, `description`, `default_value`, `sort_order`)
VALUES
(@cli_weather_forecast, 'latitude', '<latitude>', 'Number', 1, '纬度', '', 1),
(@cli_weather_forecast, 'longitude', '<longitude>', 'Number', 1, '经度', '', 2),
(@cli_weather_forecast, 'days', '[days]', 'Number', 0, '预报天数，最多16天，默认7天', '7', 3);

INSERT INTO `agent_tool_definition` (`cli_id`, `tool_name`, `description`, `url`, `method`, `auth_type`, `schema_code`, `request_schema`, `response_schema`)
VALUES (@cli_weather_forecast, 'weather_forecast_tool',
        '多日天气预报查询',
        '/v1/forecast?daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_description&timezone=auto',
        'GET', 'NONE', 'open_meteo',
        NULL, NULL);


-- ------------------------------------------------------------
-- CLI-004: 国家信息查询 (travel country info)
-- ------------------------------------------------------------
INSERT INTO `agent_cli_command` (`command_prefix`, `command_group`, `command_action`, `command_name`, `cli_template`, `description`, `command_type`, `state`, `create_user`, `modify_user`)
VALUES ('travel', 'country', 'info', 'travel_country_info',
        'travel country info <countryName>(required)',
        '查询国家或地区的详细信息，包括首都、人口、面积、语言、货币、时区、国旗、所在大洲等。用户想了解某个国家的基本情况时使用。',
        'API', 1, 'system', 'system');

SET @cli_country_info = LAST_INSERT_ID();

INSERT INTO `agent_cli_param` (`cli_id`, `param_name`, `param_flag`, `param_type`, `is_required`, `description`, `default_value`, `sort_order`)
VALUES
(@cli_country_info, 'countryName', '<countryName>', 'String', 1, '国家名称，支持英文或常见中文译名，如"Japan"、"日本"、"France"', '', 1);

INSERT INTO `agent_tool_definition` (`cli_id`, `tool_name`, `description`, `url`, `method`, `auth_type`, `schema_code`, `request_schema`, `response_schema`)
VALUES (@cli_country_info, 'country_info_tool',
        '国家信息查询',
        '/v3.1/name/{countryName}?fields=name,capital,population,area,languages,currencies,timezones,flags,region,subregion,latlng',
        'GET', 'NONE', 'rest_countries',
        NULL, NULL);


-- ------------------------------------------------------------
-- CLI-005: 货币汇率转换 (travel exchange convert)
-- ------------------------------------------------------------
INSERT INTO `agent_cli_command` (`command_prefix`, `command_group`, `command_action`, `command_name`, `cli_template`, `description`, `command_type`, `state`, `create_user`, `modify_user`)
VALUES ('travel', 'exchange', 'convert', 'travel_exchange_convert',
        'travel exchange convert <fromCurrency>(required) <toCurrency>(required) [amount]',
        '将一种货币转换为另一种货币。使用三位ISO 4217货币代码，如 USD、CNY、EUR、JPY、GBP、KRW 等。用户询问汇率换算、多少钱、换汇时使用。',
        'API', 1, 'system', 'system');

SET @cli_exchange_convert = LAST_INSERT_ID();

INSERT INTO `agent_cli_param` (`cli_id`, `param_name`, `param_flag`, `param_type`, `is_required`, `description`, `default_value`, `sort_order`)
VALUES
(@cli_exchange_convert, 'from', '<fromCurrency>', 'String', 1, '源货币代码，三位ISO代码，如 USD、CNY', '', 1),
(@cli_exchange_convert, 'to', '<toCurrency>', 'String', 1, '目标货币代码，三位ISO代码', '', 2),
(@cli_exchange_convert, 'amount', '[amount]', 'Number', 0, '兑换金额，默认1', '1', 3);

INSERT INTO `agent_tool_definition` (`cli_id`, `tool_name`, `description`, `url`, `method`, `auth_type`, `schema_code`, `request_schema`, `response_schema`)
VALUES (@cli_exchange_convert, 'exchange_convert_tool',
        '货币汇率转换',
        '/convert',
        'GET', 'NONE', 'exchange_rate',
        NULL, NULL);


-- ------------------------------------------------------------
-- CLI-006: 机票搜索 (PAGE 类型，页面跳转)
-- ------------------------------------------------------------
INSERT INTO `agent_cli_command` (`command_prefix`, `command_group`, `command_action`, `command_name`, `cli_template`, `description`, `command_type`, `state`, `create_user`, `modify_user`)
VALUES ('travel', 'flight', 'search', 'travel_flight_search',
        'travel flight search <fromCity>(required) <toCity>(required) [date]',
        '打开机票搜索页面，帮助用户查询和预订航班。当用户说"帮我订机票"、"查机票"、"飞过去多少钱"时使用。调用后会跳转到机票搜索页面。',
        'PAGE', 1, 'system', 'system');

SET @cli_flight_search = LAST_INSERT_ID();

INSERT INTO `agent_cli_param` (`cli_id`, `param_name`, `param_flag`, `param_type`, `is_required`, `description`, `default_value`, `sort_order`)
VALUES
(@cli_flight_search, 'fromCity', '<fromCity>', 'String', 1, '出发城市名称', '', 1),
(@cli_flight_search, 'toCity', '<toCity>', 'String', 1, '到达城市名称', '', 2),
(@cli_flight_search, 'date', '[date]', 'String', 0, '出发日期，格式 YYYY-MM-DD', '', 3);

INSERT INTO `agent_page_definition` (`cli_id`, `page_name`, `page_prefix`, `page_route`, `description`, `display_type`, `target_type`, `resource_project`, `resource_ids`)
VALUES (@cli_flight_search, '机票搜索', '/', 'flight/search',
        '机票搜索页面，支持出发地、目的地、日期查询',
        'PAGE', 'INTERNAL', 'travel_assistant', '');

SET @flight_page_id = LAST_INSERT_ID();

INSERT INTO `agent_page_param` (`page_id`, `param_name`, `param_type`, `is_required`, `description`)
VALUES
(@flight_page_id, 'fromCity', 'String', 1, '出发城市'),
(@flight_page_id, 'toCity', 'String', 1, '到达城市'),
(@flight_page_id, 'date', 'String', 0, '出发日期');


-- ------------------------------------------------------------
-- CLI-007: 热门景点推荐 (travel attraction list)
-- ------------------------------------------------------------
INSERT INTO `agent_cli_command` (`command_prefix`, `command_group`, `command_action`, `command_name`, `cli_template`, `description`, `command_type`, `state`, `create_user`, `modify_user`)
VALUES ('travel', 'attraction', 'list', 'travel_attraction_list',
        'travel attraction list <cityName>(required) [limit]',
        '查询指定城市的热门旅游景点列表，返回景点名称、简介、地理位置等。用户问"有什么好玩的"、"推荐景点"、"旅游攻略"时使用。',
        'API', 1, 'system', 'system');

SET @cli_attraction_list = LAST_INSERT_ID();

INSERT INTO `agent_cli_param` (`cli_id`, `param_name`, `param_flag`, `param_type`, `is_required`, `description`, `default_value`, `sort_order`)
VALUES
(@cli_attraction_list, 'cityName', '<cityName>', 'String', 1, '城市名称，支持中英文', '', 1),
(@cli_attraction_list, 'limit', '[limit]', 'Number', 0, '返回景点数量，默认10个', '10', 2);

-- 注：景点推荐用 Open-Meteo Geocoding 也可以搜索 POI，这里先用同一个网关
INSERT INTO `agent_tool_definition` (`cli_id`, `tool_name`, `description`, `url`, `method`, `auth_type`, `schema_code`, `request_schema`, `response_schema`)
VALUES (@cli_attraction_list, 'attraction_list_tool',
        '热门景点搜索',
        'https://geocoding-api.open-meteo.com/v1/search',
        'GET', 'NONE', 'open_meteo',
        NULL, NULL);


-- ============================================================
-- 三、创建旅游助手项目 (agent_project)
-- ============================================================

INSERT INTO `agent_project` (`project_name`, `project_code`, `description`, `system_prompt`, `prompt_variables`, `state`, `create_user`, `modify_user`)
VALUES (
'旅游助手',
'travel_assistant',
'智能旅游助手，提供天气查询、国家信息、汇率转换、景点推荐、机票搜索等一站式旅游咨询服务',
'你是一个专业的智能旅游助手，帮助用户规划和准备旅行。

## 你的能力
1. 地点搜索 - 通过 travel_geo_search 查询任意城市的经纬度和基本信息
2. 实时天气 - 通过 travel_weather_current 查询当前天气
3. 天气预报 - 通过 travel_weather_forecast 查询未来多日天气预报
4. 国家信息 - 通过 travel_country_info 查询国家详细信息
5. 汇率转换 - 通过 travel_exchange_convert 进行货币换算
6. 景点推荐 - 通过 travel_attraction_list 推荐城市热门景点
7. 机票搜索 - 通过 travel_flight_search 打开机票搜索页面

## 工作原则
- 当用户询问天气相关问题时，如果只有城市名，先调用 travel_geo_search 获取经纬度，再调用天气接口
- 不要编造经纬度数据，必须通过工具获取
- 涉及天气的问题，根据语境选择 current（今天/现在）或 forecast（未来/下周）
- 温度同时提供摄氏度和体感温度
- 汇率转换要说明当前汇率和换算后的金额
- 回答要简洁、实用、有条理，用列表形式呈现多条信息

## 输出格式
- 如果只有一个简单答案，直接用自然语言回答
- 如果有多个信息点，用有序列表或表格呈现
- 天气结果要说明天气状况（晴/雨/多云等），不要只给数字
- 国家信息按：首都、人口、语言、货币、时区的顺序组织

## 注意事项
- 如果需要调用工具但参数不明确，可以主动询问用户
- 不要编造信息，所有数据都来自工具调用结果
- 如果工具调用失败，友好地告知用户并建议替代方案
- 对于不确定的问题，明确告诉用户你不确定',
NULL,
1, 'system', 'system'
);

SET @project_id = LAST_INSERT_ID();


-- ============================================================
-- 四、项目模型配置 (agent_model_config)
-- ============================================================
-- 注意：请根据你实际配置的模型名称修改下面的 model_name 值
-- 推荐使用支持 function calling 的模型，如 doubao-pro / qwen-plus / gpt-4o-mini

INSERT INTO `agent_model_config` (`project_id`, `model_name`, `model_type`, `state`, `create_user`, `modify_user`)
VALUES (@project_id, 'doubao-pro', 'CHAT', 1, 'system', 'system');


-- ============================================================
-- 五、项目挂载 CLI 能力 (agent_project_cli_rel)
-- ============================================================

INSERT INTO `agent_project_cli_rel` (`project_id`, `cli_id`, `state`, `create_user`, `modify_user`) VALUES
(@project_id, @cli_geo_search, 1, 'system', 'system'),
(@project_id, @cli_weather_current, 1, 'system', 'system'),
(@project_id, @cli_weather_forecast, 1, 'system', 'system'),
(@project_id, @cli_country_info, 1, 'system', 'system'),
(@project_id, @cli_exchange_convert, 1, 'system', 'system'),
(@project_id, @cli_flight_search, 1, 'system', 'system'),
(@project_id, @cli_attraction_list, 1, 'system', 'system');


-- ============================================================
-- 数据统计
-- ============================================================
-- 网关服务: 3 个 (open_meteo, rest_countries, exchange_rate)
-- CLI 命令:  7 个
--   - API 类型: 6 个
--   - PAGE 类型: 1 个
-- CLI 参数:  约 18 个
-- 项目:     1 个 (travel_assistant)
-- 模型配置:  1 个 (需确认 model_name)
-- 能力挂载:  7 个
-- ============================================================
