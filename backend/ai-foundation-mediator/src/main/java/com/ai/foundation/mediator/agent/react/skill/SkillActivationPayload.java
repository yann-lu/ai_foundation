package com.ai.foundation.mediator.agent.react.skill;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Skill 激活载荷：模型调用 {@code skill_{code}} 后回传给模型的内容。
 * <p>
 * {@code body} = {@code skill.systemPrompt} + "\n\n" + 固定工具选型提示，
 * 由 {@link ReactSkillToolInvoker} 在回传前按 {@code activation-body-preview-max}
 * 截断。完整正文保存在 {@code ReactRunSession.activatedSkill} 供后续副作用工具读取。
 */
@Getter
@Setter
public final class SkillActivationPayload {

    /** Skill 主键。 */
    private Long skillId;

    private String skillCode;

    private String skillName;

    /** 完整正文（systemPrompt + 工具选型提示），可能超过回传预览上限。 */
    private String body;

    /** 历史字段：绑定 CLI 名；本平台运行时恒为空。 */
    private List<String> boundCliToolNames = new ArrayList<>();

    /** 历史字段：Navigate 命令；本平台运行时恒为空。 */
    private List<String> navigateCommands = new ArrayList<>();
}
