package com.ai.foundation.mediator.agent.react.skill;

import com.ai.foundation.dal.entity.AgentSkillDefinition;
import com.ai.foundation.mediator.agent.react.core.ReactRunSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 把 {@link AgentSkillDefinition} 列表转为 {@code skill_{code}} 形式的 {@link ToolCallback}。
 * <p>
 * 工具描述仅含 {@code name} + {@code description}（前 120 字），不含 {@code systemPrompt}，
 * 保证 outer prompt 长度可控；模型需要看 skill 正文时通过调用本工具，由
 * {@link ReactSkillToolInvoker} 回传预览。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReactSkillToolFactory {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "instruction": {
                  "type": "string",
                  "description": "可选：覆盖默认用户指令；不传则使用当前用户消息与附件上下文"
                }
              },
              "additionalProperties": false
            }
            """;

    private final ReactSkillToolInvoker skillToolInvoker;

    public List<ToolCallback> buildSkillTools(List<AgentSkillDefinition> skills, ReactRunSession runSession) {
        if (skills == null || skills.isEmpty()) {
            return List.of();
        }
        List<ToolCallback> tools = new ArrayList<>(skills.size());
        for (AgentSkillDefinition skill : skills) {
            if (skill == null || skill.getSkillCode() == null || skill.getSkillCode().isBlank()) {
                continue;
            }
            String toolName = ReactSkillToolNames.resolveToolName(skill);
            tools.add(buildToolCallback(toolName, skill, runSession));
            log.info("ReactSkillToolFactory register skill tool, toolName={}, skillId={}, skillCode={}, skillName={}, state={}",
                    toolName, skill.getId(), skill.getSkillCode(), skill.getSkillName(), skill.getState());
        }
        return tools;
    }

    private ToolCallback buildToolCallback(String toolName, AgentSkillDefinition skill, ReactRunSession runSession) {
        return FunctionToolCallback.builder(toolName, (ReactSkillToolInput input) ->
                        ReactRunSession.callWithSession(runSession, () -> skillToolInvoker.invoke(skill, input)))
                .description(buildDescription(skill))
                .inputSchema(INPUT_SCHEMA)
                .inputType(ReactSkillToolInput.class)
                .build();
    }

    private String buildDescription(AgentSkillDefinition skill) {
        StringBuilder sb = new StringBuilder();
        sb.append("Skill：");
        if (skill.getSkillName() != null && !skill.getSkillName().isBlank()) {
            sb.append(skill.getSkillName());
        } else {
            sb.append(skill.getSkillCode());
        }
        sb.append("（code=").append(skill.getSkillCode()).append("）。");
        if (skill.getDescription() != null && !skill.getDescription().isBlank()) {
            String desc = skill.getDescription();
            if (desc.length() > 120) {
                desc = desc.substring(0, 120) + "…";
            }
            sb.append(desc);
        }
        sb.append("调用本工具后即可读取本 Skill 的操作要点与推荐 CLI。");

        // 运行时上下文提示：让模型在选择 skill tool 之前先知道需要哪些 KV
        if (skill.getRuntimeContextTemplate() != null && !skill.getRuntimeContextTemplate().isBlank()) {
            java.util.Set<String> required = com.ai.foundation.com.prompt.RuntimeContextTemplateRenderer
                    .extractUserRequiredKeys(skill.getRuntimeContextTemplate());
            if (!required.isEmpty()) {
                sb.append("（激活需上下文：").append(String.join(", ", required)).append("）");
            }
        }
        return sb.toString();
    }
}
