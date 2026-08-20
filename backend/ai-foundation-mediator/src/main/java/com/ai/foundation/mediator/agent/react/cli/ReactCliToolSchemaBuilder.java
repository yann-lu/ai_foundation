package com.ai.foundation.mediator.agent.react.cli;

import com.ai.foundation.dal.entity.AgentCliParam;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
public final class ReactCliToolSchemaBuilder {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ReactCliToolSchemaBuilder() {
    }

    public static String buildInputSchema(List<AgentCliParam> params) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> paramsProps = new LinkedHashMap<>();
        paramsProps.put("type", "object");
        paramsProps.put("description", "CLI 命令参数键值对");
        paramsProps.put("additionalProperties", true);

        Map<String, Object> innerProps = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        if (params != null) {
            for (AgentCliParam param : params) {
                if (StringUtils.isBlank(param.getParamName())) {
                    continue;
                }
                // 凭证类参数（key / secret / token 等）不出现在工具 schema，
                // 避免模型自行填写错误值覆盖 default_value 导致 INVALID_USER_KEY。
                // 这些值由 CliParamBinder 从 default_value 注入，模型无需知晓。
                if (isCredentialParam(param.getParamName())) {
                    continue;
                }
                Map<String, Object> prop = new LinkedHashMap<>();
                prop.put("type", mapType(param.getParamType()));
                if (StringUtils.isNotBlank(param.getDescription())) {
                    prop.put("description", param.getDescription());
                }
                innerProps.put(param.getParamName(), prop);
                if (param.getIsRequired() != null && param.getIsRequired() == 1) {
                    required.add(param.getParamName());
                }
            }
        }

        if (!innerProps.isEmpty()) {
            paramsProps.put("properties", innerProps);
        }
        if (!required.isEmpty()) {
            paramsProps.put("required", required);
        }

        properties.put("params", paramsProps);
        schema.put("properties", properties);

        List<String> topRequired = new ArrayList<>();
        topRequired.add("params");
        schema.put("required", topRequired);

        try {
            return MAPPER.writeValueAsString(schema);
        } catch (Exception ex) {
            log.warn("buildInputSchema failed, fallback to empty", ex);
            return "{\"type\":\"object\",\"properties\":{\"params\":{\"type\":\"object\",\"additionalProperties\":true}},\"required\":[\"params\"]}";
        }
    }

    public static String buildParamDescription(List<AgentCliParam> params) {
        if (params == null || params.isEmpty()) {
            return "无参数";
        }
        StringBuilder sb = new StringBuilder();
        for (AgentCliParam param : params) {
            if (StringUtils.isBlank(param.getParamName())) {
                continue;
            }
            if (isCredentialParam(param.getParamName())) {
                continue;
            }
            sb.append(param.getParamName());
            if (param.getIsRequired() != null && param.getIsRequired() == 1) {
                sb.append("(必填)");
            } else {
                sb.append("(可选)");
            }
            sb.append(": ");
            sb.append(StringUtils.defaultIfBlank(param.getDescription(), ""));
            sb.append("; ");
        }
        return sb.toString().trim();
    }

    public static String buildDirectInvokeHint(List<AgentCliParam> params) {
        if (params == null || params.isEmpty()) {
            return "无参数，直接调用即可。";
        }
        long requiredCount = params.stream()
                .filter(p -> p.getIsRequired() != null && p.getIsRequired() == 1)
                .count();
        if (requiredCount == 0) {
            return "所有参数均可选，可直接调用。";
        }
        return "必填参数有 " + requiredCount + " 个，请确保传入。";
    }

    private static String mapType(String paramType) {
        if (StringUtils.isBlank(paramType)) {
            return "string";
        }
        return switch (paramType.toLowerCase()) {
            case "int", "integer", "long", "number" -> "number";
            case "boolean", "bool" -> "boolean";
            case "array", "list" -> "array";
            case "object", "json", "map" -> "object";
            default -> "string";
        };
    }

    /** 视为服务端凭证的参数名集合：这些参数不暴露给模型，避免模型自行填写错误值。 */
    private static final Set<String> CREDENTIAL_PARAM_NAMES = Set.of(
            "key", "apikey", "appkey", "secret", "token", "accesskey", "appsecret");

    private static boolean isCredentialParam(String name) {
        if (StringUtils.isBlank(name)) {
            return false;
        }
        return CREDENTIAL_PARAM_NAMES.contains(name.trim().toLowerCase(Locale.ROOT));
    }
}
