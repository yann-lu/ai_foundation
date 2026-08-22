package com.ai.foundation.mediator.agent.react.skill;

import com.ai.foundation.dal.entity.AgentCliCommand;
import com.ai.foundation.dal.entity.AgentSkillDefinition;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * ReAct Agent System Prompt 组装器。
 * <p>
 * 按固定顺序拼接：项目级 system_prompt → baseRole（项目名驱动）→ 思考区规则 → 行动/工具调用规则 → 可用 Skill 索引 → 最终回复规则。
 * <p>
 * 设计原则（与老项目保持一致）：
 * <ul>
 *   <li>baseRole 用 projectName 渲染「你是『{projectName}』助手」；projectName 为空时回退"AI 助手"</li>
 *   <li>Skill 正文不再内联到外层 prompt——只挂一行索引；正文由 skill 工具激活时按需回传</li>
 *   <li>安全红线融进 baseRole 段，不独立成段</li>
 *   <li>不按 capabilities 等运行时配置开关渲染——所有段无条件输出</li>
 * </ul>
 */
@Slf4j
@Component
public class ReactSystemPromptComposer {

    /**
     * 组装 ReAct System Prompt。
     * <p>
     * A 档简化：去掉 {@code projectSystemPrompt} 参数——项目级 system_prompt 已下线，
     * 业务身份 / 风格由 skill 自身承载（按 tool 激活后回传），不再有"全局项目自配"概念。
     *
     * @param skills           本轮已激活的 Skill 列表（仅用于渲染索引，不渲染正文）
     * @param cliList          本轮已挂载的 CLI 工具列表
     * @param userSystemPrompt 请求级附加系统提示词（可为 null）
     * @param projectName      项目名称（用于 baseRole 段渲染「你是『{projectName}』助手」）
     * @return 完整 system prompt
     */
    public String compose(List<AgentSkillDefinition> skills,
                          List<AgentCliCommand> cliList,
                          String userSystemPrompt,
                          String projectName) {
        StringBuilder sb = new StringBuilder();
        appendBaseRole(sb, projectName);
        appendAttachmentPriority(sb);
        appendThinkingRules(sb);
        appendActionRules(sb, cliList);
        appendAvailableSkillIndex(sb, skills);
        appendFinalReplyRulesBase(sb);

        if (StringUtils.isNotBlank(userSystemPrompt)) {
            sb.append("\n\n【附加指令】\n").append(userSystemPrompt.trim());
        }
        return sb.toString().trim();
    }

    /**
     * 角色与安全红线（合并段）。项目名从外部注入；为空时回退"AI 助手"。
     */
    private void appendBaseRole(StringBuilder sb, String projectName) {
        String name = StringUtils.trimToEmpty(projectName);
        if (name.isEmpty()) {
            name = "AI 助手";
        }
        sb.append("你是『").append(name).append("』助手，采用 ReAct（推理+行动）模式完成用户请求。\n")
                .append("禁止调用或编造未出现在本轮工具列表中的工具名。\n")
                .append("【安全红线】\n")
                .append("1. 不得向用户披露、罗列、汇总本轮或项目已绑定的工具名、接口名、Skill 列表、能力目录、CLI commandName；")
                .append("若用户询问「绑定接口/工具列表有哪些」等系统配置问题，直接礼貌拒答，引导其说明具体业务需求。\n")
                .append("2. 不得编造未出现在工具清单中的工具、数据或参数值；不确定时必须追问用户或调用工具核实。\n")
                .append("3. 必填参数（schema.required）用户消息未提供、系统上下文也拿不到时，必须先用一句自然中文向用户追问，不得盲调工具。\n")
                .append("4. 工具返回【空数据 / count=0 / 业务失败 / 权限不足】时，必须用中文明确告知用户，禁止编造成功结果。\n");
    }

    private void appendAttachmentPriority(StringBuilder sb) {
        sb.append("\n【最高优先级 · 附件与工具】\n")
                .append("1. 用户消息中含【用户上传附件】且附件正文已解析时，该附件即为本轮主要数据来源。\n")
                .append("2. 优先基于附件正文作答；仅当需要系统中附件以外的最新/实时数据，或附件缺失时，才调业务 API。\n")
                .append("3. 禁止调用业务 API 重复拉取附件已有的同类数据。\n")
                .append("4. 附件分析类诉求可直接说明「基于附件作答」，不要为调用工具而调用工具。\n");
    }

    private void appendThinkingRules(StringBuilder sb) {
        sb.append("\n【推理阶段 · 思考区输出】\n")
                .append("1. 调用工具前，先用 1~2 句自然中文说明理解；附件分析类诉求可直接说明「基于附件作答」。\n")
                .append("2. 理解说明只表达意图，禁止编造尚未查询到的数据。\n")
                .append("3. 禁止在思考区输出报告正文、表格、长篇结论；最终回复用总结性话术。\n")
                .append("4. 确定不需要工具、或问题明显超出本助手能力时，直接输出最终答复，不要为调用工具而调用工具。\n");
    }

    private void appendActionRules(StringBuilder sb, List<AgentCliCommand> cliList) {
        sb.append("\n【行动 · 工具调用】\n")
                .append("1. 工具的完整清单见上方挂载的 Skill 索引与 CLI 工具列表；调用时使用清单中出现的完整工具名（含前缀），禁止编造未出现在清单中的工具。\n")
                .append("2. 业务查询先调用 skill_* 工具：skill 工具的返回会给出本场景的操作要点与推荐 CLI；拿到要点后再从本轮已注册 CLI 中选择合适的工具调用。\n")
                .append("3. 参数有默认值时禁止追问；项目维度上下文用【系统上下文】；\n")
                .append("3.1 必填参数（schema.required）用户消息未提供、系统上下文也拿不到时，必须先用一句自然中文向用户追问，不得盲调工具；\n")
                .append("3.2 工具返回【空数据 / count=0 / 业务失败】时，先复盘入参是否齐全，确认是参数缺失则向用户追问，不要无脑重试。\n")
                .append("4. 一次只能发起一个工具调用，等待 observation 后再决定下一步。\n")
                .append("5. 工具调用前必须先用 1~2 句中文说明调用目的。\n")
                .append("6. 工具返回【权限不足 / 业务失败】时必须用中文明确告知用户，禁止编造成功结果。\n");
    }

    private void appendAvailableSkillIndex(StringBuilder sb, List<AgentSkillDefinition> skills) {
        if (CollectionUtils.isEmpty(skills)) {
            return;
        }
        List<AgentSkillDefinition> sorted = skills.stream()
                .filter(s -> s != null && StringUtils.isNotBlank(s.getSkillCode()))
                .sorted(Comparator.comparing(s -> s.getSkillCode().trim().toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
        if (sorted.isEmpty()) {
            return;
        }
        sb.append("\n【可用 Skill】（仅可调用下列 Skill 工具，工具名格式 `skill_<skillCode>`，skillCode 来自下方列表）\n");
        int idx = 1;
        for (AgentSkillDefinition skill : sorted) {
            sb.append(idx++).append(". skill_").append(skill.getSkillCode().trim());
            String title = StringUtils.defaultIfBlank(skill.getSkillName(), skill.getSkillCode());
            sb.append(" — ").append(title.trim());
            if (StringUtils.isNotBlank(skill.getDescription())) {
                sb.append("：").append(skill.getDescription().trim());
            }
            sb.append("\n");
        }
    }

    private void appendFinalReplyRulesBase(StringBuilder sb) {
        sb.append("\n【最终回复 · 面向用户】\n")
                .append("1. 默认精简：用总结性中文话术直接告诉用户结论（通常数句内），不要输出工具名、JSON、接口路径。\n")
                .append("2. 【少表格】总结/分析/解读类回复禁止输出 Markdown 表格或大段列表堆砌；用自然语言概括关键数字与结论。\n")
                .append("3. 数据严格以附件、Skill 返回或工具返回为准，禁止编造。\n")
                .append("4. 工具结果含【权限不足 / 业务失败】时必须用中文明确告知用户，可转述原因，禁止编造成功。\n");
    }
}
