-- ============================================================
-- 高德地图 CLI 能力初始化脚本
-- 包含：1 个 API Schema + 5 个 CLI 命令（天气/路径规划/周边搜索/静态地图/行政查询）
-- 高德 Web 服务 API 文档：https://lbs.amap.com/api/webservice/guide/api/
-- API Key: ed623120ad3e663325272cbf7a3ccfdf
-- ============================================================

SET NAMES utf8mb4;

-- ============================================================
-- 一、高德 API Schema 配置
-- ============================================================

INSERT INTO `agent_api_schema_config` (`schema_code`, `schema_name`, `base_url`, `command_prefix`, `state`, `create_user`, `modify_user`)
VALUES ('gaode_map', '高德地图 Web 服务', 'https://restapi.amap.com', 'gaode', 1, 'system', 'system');


-- ============================================================
-- 二、CLI 命令配置
-- ============================================================

-- ------------------------------------------------------------
-- CLI-001: 天气查询 (gaode weather query)
-- 文档: https://lbs.amap.com/api/webservice/guide/api/weatherinfo/
-- ------------------------------------------------------------
INSERT INTO `agent_cli_command` (`command_prefix`, `command_group`, `command_action`, `command_name`, `cli_template`, `description`, `command_type`, `state`, `create_user`, `modify_user`)
VALUES ('gaode', 'weather', 'query', 'gaode_weather_query',
        'gaode weather query <city>(required) [extensions]',
        '查询指定城市的实时天气或天气预报。输入城市名称（如"北京"、"上海"）或 adcode。默认返回实时天气，设置 extensions=all 可获取未来天气预报。用户询问天气、温度、下不下雨、冷不冷时使用。',
        'API', 1, 'system', 'system');

SET @cli_weather = LAST_INSERT_ID();

INSERT INTO `agent_cli_param` (`cli_id`, `param_name`, `param_flag`, `param_type`, `is_required`, `description`, `default_value`, `sort_order`)
VALUES
(@cli_weather, 'key', '', 'String', 1, '高德 API Key', 'ed623120ad3e663325272cbf7a3ccfdf', 0),
(@cli_weather, 'city', '<city>', 'String', 1, '城市名称或城市编码 adcode，如"北京"、"110000"', '', 1),
(@cli_weather, 'extensions', '[extensions]', 'String', 0, '气象类型：base=实时天气（默认），all=天气预报', 'base', 2);

INSERT INTO `agent_tool_definition` (`cli_id`, `tool_name`, `description`, `url`, `method`, `auth_type`, `schema_code`, `request_schema`, `response_schema`)
VALUES (@cli_weather, 'gaode_weather_tool',
        '高德天气查询，支持实时天气和未来预报',
        '/v3/weather/weatherInfo',
        'GET', 'NONE', 'gaode_map',
        NULL, NULL);


-- ------------------------------------------------------------
-- CLI-002: 驾车路径规划 (gaode direction driving)
-- 文档: https://lbs.amap.com/api/webservice/guide/api/direction/
-- ------------------------------------------------------------
INSERT INTO `agent_cli_command` (`command_prefix`, `command_group`, `command_action`, `command_name`, `cli_template`, `description`, `command_type`, `state`, `create_user`, `modify_user`)
VALUES ('gaode', 'direction', 'driving', 'gaode_direction_driving',
        'gaode direction driving <origin>(required) <destination>(required) [strategy]',
        '驾车路线规划。根据起点和终点经纬度坐标规划驾车路线，返回距离、时长、途经路段等信息。坐标格式为"经度,纬度"，如"116.481028,39.989643"。用户询问开车怎么走、驾车路线、距离多远时使用。',
        'API', 1, 'system', 'system');

SET @cli_driving = LAST_INSERT_ID();

INSERT INTO `agent_cli_param` (`cli_id`, `param_name`, `param_flag`, `param_type`, `is_required`, `description`, `default_value`, `sort_order`)
VALUES
(@cli_driving, 'key', '', 'String', 1, '高德 API Key', 'ed623120ad3e663325272cbf7a3ccfdf', 0),
(@cli_driving, 'origin', '<origin>', 'String', 1, '起点坐标，格式"经度,纬度"，如"116.481028,39.989643"', '', 1),
(@cli_driving, 'destination', '<destination>', 'String', 1, '终点坐标，格式"经度,纬度"，如"116.434446,39.908166"', '', 2),
(@cli_driving, 'strategy', '[strategy]', 'Number', 0, '路线策略：0=速度优先，1=费用优先，2=距离优先，10=躲避拥堵（默认0）', '0', 3);

INSERT INTO `agent_tool_definition` (`cli_id`, `tool_name`, `description`, `url`, `method`, `auth_type`, `schema_code`, `request_schema`, `response_schema`)
VALUES (@cli_driving, 'gaode_driving_tool',
        '驾车路线规划，返回距离、时长、导航步骤',
        '/v3/direction/driving',
        'GET', 'NONE', 'gaode_map',
        NULL, NULL);


-- ------------------------------------------------------------
-- CLI-003: 步行路径规划 (gaode direction walking)
-- 文档: https://lbs.amap.com/api/webservice/guide/api/direction/
-- ------------------------------------------------------------
INSERT INTO `agent_cli_command` (`command_prefix`, `command_group`, `command_action`, `command_name`, `cli_template`, `description`, `command_type`, `state`, `create_user`, `modify_user`)
VALUES ('gaode', 'direction', 'walking', 'gaode_direction_walking',
        'gaode direction walking <origin>(required) <destination>(required)',
        '步行路线规划。根据起点和终点经纬度坐标规划步行路线，返回距离、预计时间、步行步骤。最大支持100km。坐标格式为"经度,纬度"。用户询问走路怎么走、步行多久能到时使用。',
        'API', 1, 'system', 'system');

SET @cli_walking = LAST_INSERT_ID();

INSERT INTO `agent_cli_param` (`cli_id`, `param_name`, `param_flag`, `param_type`, `is_required`, `description`, `default_value`, `sort_order`)
VALUES
(@cli_walking, 'key', '', 'String', 1, '高德 API Key', 'ed623120ad3e663325272cbf7a3ccfdf', 0),
(@cli_walking, 'origin', '<origin>', 'String', 1, '起点坐标，格式"经度,纬度"，如"116.481028,39.989643"', '', 1),
(@cli_walking, 'destination', '<destination>', 'String', 1, '终点坐标，格式"经度,纬度"，如"116.434446,39.908166"', '', 2);

INSERT INTO `agent_tool_definition` (`cli_id`, `tool_name`, `description`, `url`, `method`, `auth_type`, `schema_code`, `request_schema`, `response_schema`)
VALUES (@cli_walking, 'gaode_walking_tool',
        '步行路线规划，返回步行距离和步骤',
        '/v3/direction/walking',
        'GET', 'NONE', 'gaode_map',
        NULL, NULL);


-- ------------------------------------------------------------
-- CLI-004: 公交路径规划 (gaode direction transit)
-- 文档: https://lbs.amap.com/api/webservice/guide/api/direction/
-- ------------------------------------------------------------
INSERT INTO `agent_cli_command` (`command_prefix`, `command_group`, `command_action`, `command_name`, `cli_template`, `description`, `command_type`, `state`, `create_user`, `modify_user`)
VALUES ('gaode', 'direction', 'transit', 'gaode_direction_transit',
        'gaode direction transit <origin>(required) <destination>(required) <city>(required) [strategy]',
        '公交/地铁换乘路线规划。根据起点和终点坐标规划公共交通换乘方案，返回公交线路、换乘次数、票价等信息。坐标格式"经度,纬度"。用户询问公交怎么坐、地铁怎么换乘时使用。',
        'API', 1, 'system', 'system');

SET @cli_transit = LAST_INSERT_ID();

INSERT INTO `agent_cli_param` (`cli_id`, `param_name`, `param_flag`, `param_type`, `is_required`, `description`, `default_value`, `sort_order`)
VALUES
(@cli_transit, 'key', '', 'String', 1, '高德 API Key', 'ed623120ad3e663325272cbf7a3ccfdf', 0),
(@cli_transit, 'origin', '<origin>', 'String', 1, '起点坐标，格式"经度,纬度"', '', 1),
(@cli_transit, 'destination', '<destination>', 'String', 1, '终点坐标，格式"经度,纬度"', '', 2),
(@cli_transit, 'city', '<city>', 'String', 1, '起点城市名称或 citycode，如"北京"或"010"', '', 3),
(@cli_transit, 'strategy', '[strategy]', 'Number', 0, '换乘策略：0=最快捷，1=最经济，2=最少换乘，3=最少步行（默认0）', '0', 4);

INSERT INTO `agent_tool_definition` (`cli_id`, `tool_name`, `description`, `url`, `method`, `auth_type`, `schema_code`, `request_schema`, `response_schema`)
VALUES (@cli_transit, 'gaode_transit_tool',
        '公交/地铁换乘路线规划',
        '/v3/direction/transit/integrated',
        'GET', 'NONE', 'gaode_map',
        NULL, NULL);


-- ------------------------------------------------------------
-- CLI-005: 周边搜索 (gaode poi around)
-- 文档: https://lbs.amap.com/api/webservice/guide/api/search#around
-- ------------------------------------------------------------
INSERT INTO `agent_cli_command` (`command_prefix`, `command_group`, `command_action`, `command_name`, `cli_template`, `description`, `command_type`, `state`, `create_user`, `modify_user`)
VALUES ('gaode', 'poi', 'around', 'gaode_poi_around',
        'gaode poi around <location>(required) [keywords] [types] [radius]',
        '搜索指定坐标周边的 POI（兴趣点），如餐厅、酒店、景点、加油站等。输入中心点坐标"经度,纬度"，可按关键词或POI类型筛选。用户询问附近有什么、周边推荐、找附近餐厅/酒店/景点时使用。',
        'API', 1, 'system', 'system');

SET @cli_poi_around = LAST_INSERT_ID();

INSERT INTO `agent_cli_param` (`cli_id`, `param_name`, `param_flag`, `param_type`, `is_required`, `description`, `default_value`, `sort_order`)
VALUES
(@cli_poi_around, 'key', '', 'String', 1, '高德 API Key', 'ed623120ad3e663325272cbf7a3ccfdf', 0),
(@cli_poi_around, 'location', '<location>', 'String', 1, '中心点坐标，格式"经度,纬度"，如"116.473168,39.993015"', '', 1),
(@cli_poi_around, 'keywords', '[keywords]', 'String', 0, '搜索关键词，如"餐厅"、"酒店"、"星巴克"，仅支持一个关键词', '', 2),
(@cli_poi_around, 'types', '[types]', 'String', 0, 'POI类型编码，多个用|分隔，如"050000"=餐饮、"100000"=住宿、"110000"=景点', '', 3),
(@cli_poi_around, 'radius', '[radius]', 'Number', 0, '搜索半径（米），范围0-50000，默认5000', '5000', 4),
(@cli_poi_around, 'offset', '[offset]', 'Number', 0, '每页返回数量，建议不超过25，默认20', '20', 5);

INSERT INTO `agent_tool_definition` (`cli_id`, `tool_name`, `description`, `url`, `method`, `auth_type`, `schema_code`, `request_schema`, `response_schema`)
VALUES (@cli_poi_around, 'gaode_poi_around_tool',
        '周边POI搜索，查找指定位置附近的餐厅/酒店/景点等',
        '/v3/place/around',
        'GET', 'NONE', 'gaode_map',
        NULL, NULL);


-- ------------------------------------------------------------
-- CLI-006: 关键词搜索 (gaode poi search)
-- 文档: https://lbs.amap.com/api/webservice/guide/api/search
-- ------------------------------------------------------------
INSERT INTO `agent_cli_command` (`command_prefix`, `command_group`, `command_action`, `command_name`, `cli_template`, `description`, `command_type`, `state`, `create_user`, `modify_user`)
VALUES ('gaode', 'poi', 'search', 'gaode_poi_search',
        'gaode poi search <keywords>(required) [city] [types]',
        '根据关键词搜索 POI（兴趣点），支持搜索地名、机构名、品牌名等。返回POI名称、地址、坐标、电话等信息。用户搜索地点、查找地址、查某个地方在哪时使用。',
        'API', 1, 'system', 'system');

SET @cli_poi_search = LAST_INSERT_ID();

INSERT INTO `agent_cli_param` (`cli_id`, `param_name`, `param_flag`, `param_type`, `is_required`, `description`, `default_value`, `sort_order`)
VALUES
(@cli_poi_search, 'key', '', 'String', 1, '高德 API Key', 'ed623120ad3e663325272cbf7a3ccfdf', 0),
(@cli_poi_search, 'keywords', '<keywords>', 'String', 1, '搜索关键词，如"故宫博物院"、"望京SOHO"', '', 1),
(@cli_poi_search, 'city', '[city]', 'String', 0, '限定搜索城市，城市名或 citycode，如"北京"，默认全国', '', 2),
(@cli_poi_search, 'types', '[types]', 'String', 0, 'POI类型编码，多个用|分隔', '', 3),
(@cli_poi_search, 'offset', '[offset]', 'Number', 0, '每页返回数量，建议不超过25，默认20', '20', 4);

INSERT INTO `agent_tool_definition` (`cli_id`, `tool_name`, `description`, `url`, `method`, `auth_type`, `schema_code`, `request_schema`, `response_schema`)
VALUES (@cli_poi_search, 'gaode_poi_search_tool',
        '关键词POI搜索，按名称搜索地点',
        '/v3/place/text',
        'GET', 'NONE', 'gaode_map',
        NULL, NULL);


-- ------------------------------------------------------------
-- CLI-007: 行政区划查询 (gaode district query)
-- 文档: https://lbs.amap.com/api/webservice/guide/api/district/
-- ------------------------------------------------------------
INSERT INTO `agent_cli_command` (`command_prefix`, `command_group`, `command_action`, `command_name`, `cli_template`, `description`, `command_type`, `state`, `create_user`, `modify_user`)
VALUES ('gaode', 'district', 'query', 'gaode_district_query',
        'gaode district query <keywords>(required) [subdistrict]',
        '查询中国行政区划信息，包括省、市、区县、街道的名称、编码(adcode)、中心坐标等。支持按名称或编码搜索。用户查询某个城市/区的编码、下级行政区划、城市层级关系时使用。',
        'API', 1, 'system', 'system');

SET @cli_district = LAST_INSERT_ID();

INSERT INTO `agent_cli_param` (`cli_id`, `param_name`, `param_flag`, `param_type`, `is_required`, `description`, `default_value`, `sort_order`)
VALUES
(@cli_district, 'key', '', 'String', 1, '高德 API Key', 'ed623120ad3e663325272cbf7a3ccfdf', 0),
(@cli_district, 'keywords', '<keywords>', 'String', 1, '搜索关键词，支持行政区名称、citycode 或 adcode，如"北京"、"110000"', '', 1),
(@cli_district, 'subdistrict', '[subdistrict]', 'Number', 0, '返回下级行政区层级：0=不返回，1=下一级，2=下两级，3=下三级（默认1）', '1', 2);

INSERT INTO `agent_tool_definition` (`cli_id`, `tool_name`, `description`, `url`, `method`, `auth_type`, `schema_code`, `request_schema`, `response_schema`)
VALUES (@cli_district, 'gaode_district_tool',
        '行政区划查询，获取省市区编码和层级关系',
        '/v3/config/district',
        'GET', 'NONE', 'gaode_map',
        NULL, NULL);


-- ------------------------------------------------------------
-- CLI-008: 地理编码 (gaode geo code)
-- 文档: https://lbs.amap.com/api/webservice/guide/api/georegeo/
-- ------------------------------------------------------------
INSERT INTO `agent_cli_command` (`command_prefix`, `command_group`, `command_action`, `command_name`, `cli_template`, `description`, `command_type`, `state`, `create_user`, `modify_user`)
VALUES ('gaode', 'geo', 'code', 'gaode_geo_code',
        'gaode geo code <address>(required) [city]',
        '将结构化地址转换为经纬度坐标。输入详细地址（如"北京市朝阳区阜通东大街6号"），返回精确的经纬度。其他高德工具需要坐标时，可先通过本工具将地址转为坐标。',
        'API', 1, 'system', 'system');

SET @cli_geo_code = LAST_INSERT_ID();

INSERT INTO `agent_cli_param` (`cli_id`, `param_name`, `param_flag`, `param_type`, `is_required`, `description`, `default_value`, `sort_order`)
VALUES
(@cli_geo_code, 'key', '', 'String', 1, '高德 API Key', 'ed623120ad3e663325272cbf7a3ccfdf', 0),
(@cli_geo_code, 'address', '<address>', 'String', 1, '结构化地址，如"北京市朝阳区阜通东大街6号"', '', 1),
(@cli_geo_code, 'city', '[city]', 'String', 0, '指定查询城市，城市名或 citycode，可选', '', 2);

INSERT INTO `agent_tool_definition` (`cli_id`, `tool_name`, `description`, `url`, `method`, `auth_type`, `schema_code`, `request_schema`, `response_schema`)
VALUES (@cli_geo_code, 'gaode_geo_code_tool',
        '地理编码，地址转坐标',
        '/v3/geocode/geo',
        'GET', 'NONE', 'gaode_map',
        NULL, NULL);


-- ============================================================
-- 三、召回标签（帮助 Agent 匹配用户意图到 CLI 命令）
-- ============================================================

-- 天气查询
SET @tag_cli = @cli_weather;
INSERT INTO `agent_cli_recall_tag` (`cli_id`, `tag_type`, `tag_value`, `weight`, `match_mode`, `sort_order`, `state`) VALUES
(@tag_cli, 'ALIAS', '天气', 10, 'contains', 1, 1),
(@tag_cli, 'ALIAS', '温度', 8, 'contains', 2, 1),
(@tag_cli, 'ALIAS', '下雨', 8, 'contains', 3, 1),
(@tag_cli, 'ALIAS', 'weather', 10, 'contains', 4, 1),
(@tag_cli, 'DOMAIN', '气象', 5, 'contains', 5, 1);

-- 驾车路径
SET @tag_cli = @cli_driving;
INSERT INTO `agent_cli_recall_tag` (`cli_id`, `tag_type`, `tag_value`, `weight`, `match_mode`, `sort_order`, `state`) VALUES
(@tag_cli, 'ALIAS', '开车', 10, 'contains', 1, 1),
(@tag_cli, 'ALIAS', '驾车', 10, 'contains', 2, 1),
(@tag_cli, 'ALIAS', '自驾', 8, 'contains', 3, 1),
(@tag_cli, 'ALIAS', '导航', 8, 'contains', 4, 1),
(@tag_cli, 'DOMAIN', '出行', 5, 'contains', 5, 1);

-- 步行路径
SET @tag_cli = @cli_walking;
INSERT INTO `agent_cli_recall_tag` (`cli_id`, `tag_type`, `tag_value`, `weight`, `match_mode`, `sort_order`, `state`) VALUES
(@tag_cli, 'ALIAS', '步行', 10, 'contains', 1, 1),
(@tag_cli, 'ALIAS', '走路', 10, 'contains', 2, 1),
(@tag_cli, 'ALIAS', '走过去', 8, 'contains', 3, 1);

-- 公交路径
SET @tag_cli = @cli_transit;
INSERT INTO `agent_cli_recall_tag` (`cli_id`, `tag_type`, `tag_value`, `weight`, `match_mode`, `sort_order`, `state`) VALUES
(@tag_cli, 'ALIAS', '公交', 10, 'contains', 1, 1),
(@tag_cli, 'ALIAS', '地铁', 10, 'contains', 2, 1),
(@tag_cli, 'ALIAS', '换乘', 8, 'contains', 3, 1),
(@tag_cli, 'ALIAS', '坐车', 8, 'contains', 4, 1);

-- 周边搜索
SET @tag_cli = @cli_poi_around;
INSERT INTO `agent_cli_recall_tag` (`cli_id`, `tag_type`, `tag_value`, `weight`, `match_mode`, `sort_order`, `state`) VALUES
(@tag_cli, 'ALIAS', '附近', 10, 'contains', 1, 1),
(@tag_cli, 'ALIAS', '周边', 10, 'contains', 2, 1),
(@tag_cli, 'ALIAS', '旁边', 8, 'contains', 3, 1),
(@tag_cli, 'ALIAS', '有什么', 6, 'contains', 4, 1),
(@tag_cli, 'DOMAIN', 'POI', 5, 'contains', 5, 1);

-- 关键词搜索
SET @tag_cli = @cli_poi_search;
INSERT INTO `agent_cli_recall_tag` (`cli_id`, `tag_type`, `tag_value`, `weight`, `match_mode`, `sort_order`, `state`) VALUES
(@tag_cli, 'ALIAS', '搜索', 8, 'contains', 1, 1),
(@tag_cli, 'ALIAS', '在哪', 8, 'contains', 2, 1),
(@tag_cli, 'ALIAS', '地址', 8, 'contains', 3, 1),
(@tag_cli, 'ALIAS', '查找', 6, 'contains', 4, 1);

-- 行政区划
SET @tag_cli = @cli_district;
INSERT INTO `agent_cli_recall_tag` (`cli_id`, `tag_type`, `tag_value`, `weight`, `match_mode`, `sort_order`, `state`) VALUES
(@tag_cli, 'ALIAS', '行政区', 10, 'contains', 1, 1),
(@tag_cli, 'ALIAS', '区域', 6, 'contains', 2, 1),
(@tag_cli, 'ALIAS', 'adcode', 10, 'contains', 3, 1),
(@tag_cli, 'ALIAS', '城市编码', 10, 'contains', 4, 1);

-- 地理编码
SET @tag_cli = @cli_geo_code;
INSERT INTO `agent_cli_recall_tag` (`cli_id`, `tag_type`, `tag_value`, `weight`, `match_mode`, `sort_order`, `state`) VALUES
(@tag_cli, 'ALIAS', '坐标', 8, 'contains', 1, 1),
(@tag_cli, 'ALIAS', '经纬度', 8, 'contains', 2, 1),
(@tag_cli, 'ALIAS', '地址转坐标', 10, 'contains', 3, 1),
(@tag_cli, 'DOMAIN', '地理', 5, 'contains', 4, 1);


-- ============================================================
-- 数据统计
-- ============================================================
-- API Schema:  1 个 (gaode_map)
-- CLI 命令:    8 个（全部 API 类型）
--   - gaode_weather_query     天气查询（实时+预报）
--   - gaode_direction_driving 驾车路径规划
--   - gaode_direction_walking 步行路径规划
--   - gaode_direction_transit 公交路径规划
--   - gaode_poi_around        周边搜索
--   - gaode_poi_search        关键词搜索
--   - gaode_district_query    行政区划查询
--   - gaode_geo_code          地理编码（地址转坐标）
-- CLI 参数:    约 28 个
-- 召回标签:    约 30 个
-- ============================================================
