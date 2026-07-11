package com.ai.foundation.mediator.run;

import com.ai.foundation.biz.conversation.AgentMessageService;
import com.ai.foundation.biz.run.AgentRunService;
import com.ai.foundation.com.constant.RunTypeConstant;
import com.ai.foundation.com.enums.RunStateEnum;
import com.ai.foundation.com.enums.RunStreamEventTypeEnum;
import com.ai.foundation.dal.entity.AgentConversationInfo;
import com.ai.foundation.dal.entity.AgentMessageInfo;
import com.ai.foundation.dal.entity.AgentRun;
import com.ai.foundation.mediator.chat.AiChatMedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentOrchestrator {

    private final AiChatMedService aiChatMedService;
    private final AgentRunService runService;
    private final AgentMessageService messageService;
    private final RunEventEmitter runEventEmitter;

    /**
     * 启动 Run 全流程（异步调用）。
     *
     * @param run           已持久化的 Run
     * @param conversation  会话
     * @param userMessage   用户消息原文
     * @param systemPrompt  系统提示词（可选）
     */
    public void startRun(AgentRun run, AgentConversationInfo conversation,
                         String userMessage, String systemPrompt) {
        String runCode = run.getRunCode();
        String conversationCode = conversation.getConversationCode();
        long startTime = System.currentTimeMillis();

        try {
            updateRunState(run, RunTypeConstant.CHAT, RunStateEnum.EXECUTING, 0);
            runEventEmitter.emit(runCode, conversationCode, RunStreamEventTypeEnum.RUN_START,
                    RunStateEnum.EXECUTING.getCode(), null);
            runEventEmitter.emit(runCode, conversationCode, RunStreamEventTypeEnum.CHAT_START,
                    RunStateEnum.EXECUTING.getCode(), null);

            StringBuilder contentBuilder = new StringBuilder();

            aiChatMedService.streamTokens(conversation, userMessage, systemPrompt, null)
                    .doOnNext(token -> {
                        contentBuilder.append(token);
                        runEventEmitter.emit(runCode, conversationCode,
                                RunStreamEventTypeEnum.CHAT_TOKEN,
                                RunStateEnum.EXECUTING.getCode(), token);
                    })
                    .doOnError(err -> log.error("流式对话失败 runCode={}", runCode, err))
                    .blockLast();

            String reply = contentBuilder.toString();
            long duration = System.currentTimeMillis() - startTime;

            if (StringUtils.isNotBlank(reply)) {
                aiChatMedService.saveAssistantMessage(conversation, reply, duration);
            }

            runEventEmitter.emit(runCode, conversationCode, RunStreamEventTypeEnum.CHAT_COMPLETE,
                    RunStateEnum.COMPLETED.getCode(), null);
            updateRunState(run, RunTypeConstant.CHAT, RunStateEnum.COMPLETED, 1);
            runEventEmitter.finishRun(runCode, conversationCode,
                    RunStateEnum.COMPLETED.getCode(), reply);

            log.info("Run 完成 runCode={} duration={}ms replyLength={}", runCode, duration,
                    reply == null ? 0 : reply.length());
        } catch (Exception e) {
            log.error("Run 执行失败 runCode={}", runCode, e);
            updateRunState(run, RunTypeConstant.CHAT, RunStateEnum.FAILED, 2);
            runEventEmitter.failRun(runCode, conversationCode,
                    e.getMessage() != null ? e.getMessage() : "执行失败");
        }
    }

    /**
     * 取消 Run：推送取消事件并更新状态。
     *
     * @param run              已持久化的 Run
     * @param conversationCode 会话编码
     */
    public void cancelRun(AgentRun run, String conversationCode) {
        updateRunState(run, run.getRunType(), RunStateEnum.CANCELLED, 2);
        runEventEmitter.cancelRun(run.getRunCode(), conversationCode);
    }

    private void updateRunState(AgentRun run, String runType, RunStateEnum state, int compatState) {
        AgentRun update = new AgentRun();
        update.setId(run.getId());
        update.setRunType(runType);
        update.setTaskState(state.getCode());
        update.setState(compatState);
        update.setUpdateTime(LocalDateTime.now());
        runService.updateById(update);
        run.setRunType(runType);
        run.setTaskState(state.getCode());
        run.setState(compatState);
    }
}
