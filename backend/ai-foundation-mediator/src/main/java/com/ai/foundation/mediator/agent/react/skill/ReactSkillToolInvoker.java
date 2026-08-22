package com.ai.foundation.mediator.agent.react.skill;

import com.ai.foundation.dal.entity.AgentSkillDefinition;
import com.ai.foundation.mediator.agent.context.AgentExecutionContext;
import com.ai.foundation.mediator.agent.react.core.ReactRunSession;
import com.ai.foundation.mediator.config.AgentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Skill 激活处理器：模型调用 {@code skill_{code}} 后，读取 skill 行 → 组装正文 → 截断 → 回传。
 * <p>
 * 与老项目对齐：纯函数无嵌套 LLM；正文预览长度由
 * {@code agent.react.activation-body-preview-max} 控制（默认 4000 字符）。
 * <p>
 * 完整 body 写入 {@link ReactRunSession#activatedSkill}，供后续副作用工具（导出/分页等）读取，
 * 不二次注入 outer prompt。
 * <p>
 * 若 skill 配置了 {@code runtimeContextTemplate}，激活时按会话上下文变量做
 * {@code #{var}} 占位符替换（用户业务变量），追加【当前会话上下文】段到 body 末尾。
 * <p>
 * 平台系统级变量（{@code todayDate} / {@code currentTime} / {@code currentTimezone}）
 * 无条件追加【当前时间】段到 body 末尾——不依赖模板是否配置。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReactSkillToolInvoker {

    private static final int DEFAULT_ACTIVATION_BODY_PREVIEW_MAX = 4000;
    private static final String REGISTERED_TOOL_HINT =
            "请从本轮已注册工具中选择合适的 API/NAVIGATE/knowledge 工具；不要再调用本 Skill 工具。";
    private static final String RUNTIME_CONTEXT_HEADER = "\n\n【当前会话上下文】";
    private static final String AUTO_RUNTIME_HEADER = "\n\n【当前时间】";

    private final AgentProperties agentProperties;
    private final RuntimeContextTemplateInjector templateInjector;

    public String invoke(AgentSkillDefinition skill, ReactSkillToolInput input) {
        ReactRunSession session = ReactRunSession.current();
        if (session == null) {
            throw new IllegalStateException("ReAct 会话未绑定");
        }
        if (skill == null || skill.getId() == null) {
            throw new IllegalArgumentException("Skill 实体缺失");
        }

        String toolName = ReactSkillToolNames.resolveToolName(skill);
        long startMs = System.currentTimeMillis();
        log.info("ReactSkillToolInvoker begin, runCode={}, toolName={}, skillId={}, skillCode={}",
                session.getRunCode(), toolName, skill.getId(), skill.getSkillCode());

        String result;
        try {
            AgentExecutionContext context = session.getExecutionContext();
            SkillActivationPayload payload = assemblePayload(skill, context);
            session.activateSkill(payload);
            result = formatActivationText(payload, resolveActivationBodyPreviewMax());
        } catch (Exception ex) {
            log.info("[AgentPerf] stage=skillToolTotal runCode={} toolName={} outcome=failed err={}",
                    session.getRunCode(), toolName, ex.getMessage());
            throw ex;
        }
        log.info("ReactSkillToolInvoker activated, runCode={}, toolName={}, resultLength={}, costMs={}",
                session.getRunCode(), toolName, result.length(), System.currentTimeMillis() - startMs);
        return StringUtils.defaultIfBlank(result, "(无返回内容)");
    }

    private SkillActivationPayload assemblePayload(AgentSkillDefinition skill, AgentExecutionContext context) {
        String systemPrompt = StringUtils.defaultString(skill.getSystemPrompt()).trim();
        StringBuilder body = new StringBuilder();
        if (StringUtils.isNotBlank(systemPrompt)) {
            body.append(systemPrompt);
        }
        if (!body.isEmpty()) {
            body.append("\n\n");
        }
        body.append(REGISTERED_TOOL_HINT);

        // 业务级运行时上下文段：按 skill.runtimeContextTemplate 渲染 #{var} 占位
        if (StringUtils.isNotBlank(skill.getRuntimeContextTemplate())) {
            String rendered = templateInjector.render(skill.getRuntimeContextTemplate(), context);
            if (StringUtils.isNotBlank(rendered)) {
                body.append(RUNTIME_CONTEXT_HEADER).append('\n').append(rendered);
            }
        }

        // 平台系统级运行时上下文：todayDate / currentTime / currentTimezone 无条件追加
        if (context != null) {
            body.append(AUTO_RUNTIME_HEADER).append('\n')
                    .append("- 今日日期: ").append(context.getTodayDate()).append('\n')
                    .append("- 当前时间: ").append(context.getCurrentTime()).append('\n')
                    .append("- 时区: ").append(context.getCurrentTimezone());
        }

        SkillActivationPayload payload = new SkillActivationPayload();
        payload.setSkillId(skill.getId());
        payload.setSkillCode(skill.getSkillCode());
        payload.setSkillName(skill.getSkillName());
        payload.setBody(body.toString());
        return payload;
    }

    private int resolveActivationBodyPreviewMax() {
        if (agentProperties == null || agentProperties.getReact() == null) {
            return DEFAULT_ACTIVATION_BODY_PREVIEW_MAX;
        }
        int v = agentProperties.getReact().getActivationBodyPreviewMax();
        return v > 0 ? v : DEFAULT_ACTIVATION_BODY_PREVIEW_MAX;
    }

    private String formatActivationText(SkillActivationPayload payload, int previewMax) {
        String skillCode = payload == null ? "" : StringUtils.defaultString(payload.getSkillCode());
        String skillName = payload == null ? "" : StringUtils.defaultString(payload.getSkillName());
        String body = payload == null ? "" : StringUtils.defaultString(payload.getBody());

        StringBuilder sb = new StringBuilder();
        sb.append("【Skill 已激活】").append(skillCode);
        if (StringUtils.isNotBlank(skillName)) {
            sb.append("（").append(skillName.trim()).append('）');
        }
        sb.append('\n');
        sb.append(REGISTERED_TOOL_HINT).append('\n');
        if (StringUtils.isNotBlank(body)) {
            sb.append("【操作要点预览】\n");
            if (body.length() <= previewMax) {
                sb.append(body);
            } else {
                sb.append(body, 0, previewMax);
                sb.append("\n…(已截断，完整说明已缓存于会话；请结合本轮已注册工具 schema 填参)");
            }
        }
        return sb.toString();
    }
}
