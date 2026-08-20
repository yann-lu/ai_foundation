package com.ai.foundation.mediator.agent.react.stream;

import com.ai.foundation.com.enums.RunStateEnum;
import com.ai.foundation.com.enums.RunStreamEventTypeEnum;
import com.ai.foundation.mediator.agent.react.core.ReactRunSession;
import com.ai.foundation.mediator.agent.react.dto.ToolStatusDto;
import com.ai.foundation.mediator.run.RunEventEmitter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * 工具执行状态 SSE 推送器：把 running / success / failed 三态通过
 * {@link RunStreamEventTypeEnum#TOOL_STATUS} 事件推送到 {@link com.ai.foundation.mediator.run.RunEventBus}。
 *
 * <p>调用方：{@link com.ai.foundation.mediator.agent.react.core.StatusAwareToolCallback}。
 * 每次工具执行会触发「running → (success | failed)」两个事件，前端工具卡片据此切换状态。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReactToolStatusEmitter {

    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_FAILED = "failed";

    private final RunEventEmitter runEventEmitter;

    /**
     * 工具开始执行时推送 running 状态。
     *
     * @param session  当前 Run 会话（提供 runCode / conversationCode）
     * @param toolName 工具名
     */
    public void publishRunning(ReactRunSession session, String toolName) {
        emit(session, ToolStatusDto.builder()
                .toolName(toolName)
                .status(STATUS_RUNNING)
                .costMs(null)
                .errorMessage(null)
                .timestamp(System.currentTimeMillis())
                .build());
    }

    /**
     * 工具成功完成时推送 success 状态。
     *
     * @param session  当前 Run 会话
     * @param toolName 工具名
     * @param costMs   耗时（毫秒）
     */
    public void publishSuccess(ReactRunSession session, String toolName, long costMs) {
        emit(session, ToolStatusDto.builder()
                .toolName(toolName)
                .status(STATUS_SUCCESS)
                .costMs(costMs)
                .errorMessage(null)
                .timestamp(System.currentTimeMillis())
                .build());
    }

    /**
     * 工具执行失败时推送 failed 状态。
     *
     * @param session       当前 Run 会话
     * @param toolName      工具名
     * @param errorMessage  失败原因（可空）
     * @param costMs        耗时（毫秒）
     */
    public void publishFailed(ReactRunSession session, String toolName, String errorMessage, long costMs) {
        emit(session, ToolStatusDto.builder()
                .toolName(toolName)
                .status(STATUS_FAILED)
                .costMs(costMs)
                .errorMessage(StringUtils.defaultString(errorMessage))
                .timestamp(System.currentTimeMillis())
                .build());
    }

    private void emit(ReactRunSession session, ToolStatusDto dto) {
        if (session == null || dto == null) {
            return;
        }
        String runCode = session.getRunCode();
        String conversationCode = session.getConversationCode();
        if (StringUtils.isBlank(runCode)) {
            return;
        }
        runEventEmitter.emit(runCode, conversationCode,
                RunStreamEventTypeEnum.TOOL_STATUS,
                RunStateEnum.EXECUTING.getCode(),
                dto);
        if (log.isDebugEnabled()) {
            log.debug("TOOL_STATUS runCode={} toolName={} status={} costMs={}",
                    runCode, dto.getToolName(), dto.getStatus(), dto.getCostMs());
        }
    }
}
