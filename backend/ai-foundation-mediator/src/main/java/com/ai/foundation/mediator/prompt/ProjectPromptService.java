package com.ai.foundation.mediator.prompt;

import com.ai.foundation.com.exception.BusinessException;
import com.ai.foundation.com.response.ResultCode;
import com.ai.foundation.dal.entity.AgentConversationInfo;
import com.ai.foundation.dal.entity.AgentProject;
import com.ai.foundation.facade.dto.conversation.ConversationCreateRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ProjectPromptService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy年M月d日");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{\\s*([A-Za-z][A-Za-z0-9_.-]*)\\s*}}");
    private static final TypeReference<List<PromptVariableDefinition>> VARIABLE_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> VARIABLE_MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public void validateProjectConfig(String promptVariables) {
        parseDefinitions(promptVariables);
    }

    public String buildConversationVariables(AgentProject project, ConversationCreateRequest request) {
        Map<String, Object> variables = new LinkedHashMap<>();
        if (request.getContextVariables() != null) {
            variables.putAll(request.getContextVariables());
        }
        putIfNotBlank(variables, "blocCode", request.getBlocCode());
        putIfNotBlank(variables, "hotelCode", request.getHotelCode());

        List<PromptVariableDefinition> definitions = parseDefinitions(project.getPromptVariables());
        applyDefaults(variables, definitions);
        validateVariables(project.getSystemPrompt(), definitions, variables);
        return toJson(variables);
    }

    public String buildSystemPrompt(AgentProject project, AgentConversationInfo conversation,
                                    String summaryBlock, String requestSystemPrompt, String defaultSystemPrompt) {
        Map<String, Object> variables = parseVariableValues(conversation.getContextVariables());
        String projectPrompt = project == null ? null : renderPrompt(project.getSystemPrompt(), variables);
        String contextBlock = formatContextBlock(variables);
        String datetimeBlock = buildDatetimeBlock();

        StringBuilder sb = new StringBuilder();
        appendBlock(sb, datetimeBlock);
        if (StringUtils.isNotBlank(projectPrompt)) {
            appendBlock(sb, projectPrompt);
        }
        appendBlock(sb, contextBlock);
        appendBlock(sb, summaryBlock);
        appendBlock(sb, requestSystemPrompt);
        if (sb.isEmpty() && StringUtils.isNotBlank(defaultSystemPrompt)) {
            sb.append(defaultSystemPrompt.trim());
        }
        return sb.toString();
    }

    private List<PromptVariableDefinition> parseDefinitions(String promptVariables) {
        if (StringUtils.isBlank(promptVariables)) {
            return List.of();
        }
        try {
            List<PromptVariableDefinition> definitions = objectMapper.readValue(promptVariables, VARIABLE_LIST_TYPE);
            return definitions == null ? List.of() : definitions;
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "项目提示词变量定义不是合法JSON数组");
        }
    }

    private Map<String, Object> parseVariableValues(String contextVariables) {
        if (StringUtils.isBlank(contextVariables)) {
            return Map.of();
        }
        try {
            Map<String, Object> variables = objectMapper.readValue(contextVariables, VARIABLE_MAP_TYPE);
            return variables == null ? Map.of() : variables;
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "会话上下文变量不是合法JSON对象");
        }
    }

    private void applyDefaults(Map<String, Object> variables, List<PromptVariableDefinition> definitions) {
        for (PromptVariableDefinition definition : definitions) {
            if (definition == null || StringUtils.isBlank(definition.getName())) {
                continue;
            }
            String name = definition.getName().trim();
            if (!hasValue(variables.get(name)) && definition.getDefaultValue() != null) {
                variables.put(name, definition.getDefaultValue());
            }
        }
    }

    private void validateVariables(String systemPrompt, List<PromptVariableDefinition> definitions,
                                   Map<String, Object> variables) {
        Set<String> missing = new LinkedHashSet<>();
        for (PromptVariableDefinition definition : definitions) {
            if (definition == null || StringUtils.isBlank(definition.getName())) {
                continue;
            }
            String name = definition.getName().trim();
            if (Boolean.TRUE.equals(definition.getRequired()) && !hasValue(variables.get(name))) {
                missing.add(name);
            }
            validateType(name, definition.getType(), variables.get(name));
        }
        for (String name : extractPlaceholders(systemPrompt)) {
            if (!hasValue(variables.get(name))) {
                missing.add(name);
            }
        }
        if (!missing.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_INVALID,
                    "创建会话失败：项目提示词缺少必要变量 " + String.join("、", missing));
        }
    }

    private void validateType(String name, String type, Object value) {
        if (!hasValue(value) || StringUtils.isBlank(type)) {
            return;
        }
        String normalizedType = type.trim().toLowerCase();
        boolean valid = switch (normalizedType) {
            case "string" -> value instanceof String;
            case "number" -> value instanceof Number;
            case "boolean" -> value instanceof Boolean;
            default -> true;
        };
        if (!valid) {
            throw new BusinessException(ResultCode.PARAM_INVALID,
                    "创建会话失败：变量 " + name + " 类型必须为 " + normalizedType);
        }
    }

    private List<String> extractPlaceholders(String content) {
        if (StringUtils.isBlank(content)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(content);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private String renderPrompt(String systemPrompt, Map<String, Object> variables) {
        if (StringUtils.isBlank(systemPrompt)) {
            return null;
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(systemPrompt);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            Object value = variables.get(matcher.group(1));
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value == null ? "" : String.valueOf(value)));
        }
        matcher.appendTail(sb);
        return sb.toString().trim();
    }

    private String buildDatetimeBlock() {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        return "今天日期：" + today.format(DATE_FORMATTER) + "\n"
                + "当前时间：" + now.format(DATETIME_FORMATTER);
    }

    private String formatContextBlock(Map<String, Object> variables) {
        if (variables.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("【系统上下文】");
        variables.forEach((key, value) -> {
            if (hasValue(value)) {
                sb.append('\n').append("- ").append(key).append("：").append(value);
            }
        });
        sb.append('\n').append("请结合以上上下文理解用户诉求；涉及租户、权限或业务范围时以上下文为准。");
        return sb.toString();
    }

    private String toJson(Map<String, Object> variables) {
        if (variables.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(variables);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "会话上下文变量序列化失败");
        }
    }

    private boolean hasValue(Object value) {
        if (value == null) {
            return false;
        }
        return !(value instanceof String text) || StringUtils.isNotBlank(text);
    }

    private void putIfNotBlank(Map<String, Object> variables, String key, String value) {
        if (StringUtils.isNotBlank(value) && !hasValue(variables.get(key))) {
            variables.put(key, value.trim());
        }
    }

    private void appendBlock(StringBuilder sb, String block) {
        if (StringUtils.isBlank(block)) {
            return;
        }
        if (!sb.isEmpty()) {
            sb.append("\n\n");
        }
        sb.append(block.trim());
    }
}
