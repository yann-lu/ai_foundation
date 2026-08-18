package com.ai.foundation.mediator.agent.react.skill;

import com.ai.foundation.dal.entity.AgentSkillDefinition;

/**
 * Skill 能力扩展点 SPI。
 * <p>
 * 业务方通过实现该接口注入：
 * <ul>
 *   <li>{@link #promptHint()} — 简短提示词（始终展示）</li>
 *   <li>{@link #hardRule()} — 硬规则（始终展示）</li>
 *   <li>{@link #actionRule(AgentSkillDefinition)} — 激活该 Skill 时的额外行动规则</li>
 *   <li>{@link #skillToolAddon(AgentSkillDefinition)} — Skill 工具附加说明</li>
 *   <li>{@link #fileArtifactAddon(AgentSkillDefinition)} — 文件产物附加规则</li>
 * </ul>
 * capabilityId 全局唯一（如 {@code chart} / {@code table-excel}），由 {@link SkillCapabilityRegistry} 索引。
 */
public interface SkillCapabilityHandler {

    String capabilityId();

    String promptHint();

    String hardRule();

    default String actionRule(AgentSkillDefinition skill) {
        return "";
    }

    default String skillToolAddon(AgentSkillDefinition skill) {
        return "";
    }

    default String fileArtifactAddon(AgentSkillDefinition skill) {
        return "";
    }
}
