package com.ai.foundation.mediator.agent.react.skill;

import com.ai.foundation.dal.entity.AgentSkillDefinition;
import org.apache.commons.lang3.StringUtils;

/**
 * Skill 工具名解析器：{@code skill_{skillCode}} 格式，sanitize 后小写。
 */
public final class ReactSkillToolNames {

    public static final String PREFIX = "skill_";

    private static final String UNKNOWN = "skill_unknown";

    private ReactSkillToolNames() {
    }

    public static String resolveToolName(AgentSkillDefinition skill) {
        if (skill == null) {
            return UNKNOWN;
        }
        if (StringUtils.isNotBlank(skill.getSkillCode())) {
            return sanitize(PREFIX + skill.getSkillCode());
        }
        return sanitize(PREFIX + skill.getId());
    }

    private static String sanitize(String raw) {
        if (StringUtils.isBlank(raw)) {
            return UNKNOWN;
        }
        return raw.trim()
                .replaceAll("[^a-zA-Z0-9_\\-]", "_")
                .replaceAll("_+", "_")
                .toLowerCase();
    }
}
