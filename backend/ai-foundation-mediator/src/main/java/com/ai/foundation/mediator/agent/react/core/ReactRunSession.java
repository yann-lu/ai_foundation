package com.ai.foundation.mediator.agent.react.core;

import com.ai.foundation.dal.entity.AgentCliCommand;
import com.ai.foundation.dal.entity.AgentSkillDefinition;
import com.ai.foundation.mediator.agent.react.skill.SkillActivationPayload;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

@Slf4j
@Data
public class ReactRunSession {

    private static final ThreadLocal<ReactRunSession> HOLDER = new ThreadLocal<>();

    private Long runId;
    private String runCode;
    private String conversationCode;
    private String userMessage;
    private String accessToken;
    private String modelName;
    private Long projectId;
    private com.ai.foundation.mediator.agent.context.AgentExecutionContext executionContext;
    private List<AgentCliCommand> availableCliCommands = new ArrayList<>();
    private List<AgentSkillDefinition> availableSkills = new ArrayList<>();
    private List<String> toolInterpretedResults = new ArrayList<>();
    private int toolInvokeCount = 0;

    /**
     * 当前 Run 已激活的 Skill 载荷（单槽，再次激活会覆盖上一次）。
     */
    private SkillActivationPayload activatedSkill;

    public static ReactRunSession current() {
        return HOLDER.get();
    }

    public static void set(ReactRunSession session) {
        HOLDER.set(session);
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static <T> T callWithSession(ReactRunSession session, Callable<T> callable) {
        ReactRunSession prev = HOLDER.get();
        HOLDER.set(session);
        try {
            return callable.call();
        } catch (Exception ex) {
            if (ex instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(ex);
        } finally {
            if (prev != null) {
                HOLDER.set(prev);
            } else {
                HOLDER.remove();
            }
        }
    }

    public void markToolInvoked() {
        this.toolInvokeCount++;
    }

    public void activateSkill(SkillActivationPayload payload) {
        this.activatedSkill = payload;
    }

    public Optional<SkillActivationPayload> currentActivatedSkill() {
        return Optional.ofNullable(activatedSkill);
    }
}
