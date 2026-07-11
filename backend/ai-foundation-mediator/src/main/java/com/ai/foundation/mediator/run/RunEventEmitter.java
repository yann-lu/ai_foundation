package com.ai.foundation.mediator.run;

import com.ai.foundation.com.enums.RunStreamEventTypeEnum;
import com.ai.foundation.com.stream.RunStreamEnvelope;
import org.springframework.stereotype.Component;

@Component
public class RunEventEmitter {

    private final RunEventBus runEventBus;

    public RunEventEmitter(RunEventBus runEventBus) {
        this.runEventBus = runEventBus;
    }

    public void emit(String runCode, String conversationCode,
                     RunStreamEventTypeEnum eventType, String taskState, Object data) {
        RunStreamEnvelope envelope = new RunStreamEnvelope();
        envelope.setEventType(eventType.getCode());
        envelope.setRunCode(runCode);
        envelope.setConversationCode(conversationCode);
        envelope.setTimestamp(System.currentTimeMillis());
        envelope.setTaskState(taskState);
        envelope.setData(data);
        runEventBus.publish(runCode, envelope);
    }

    public void finishRun(String runCode, String conversationCode, String taskState, Object data) {
        emit(runCode, conversationCode, RunStreamEventTypeEnum.RUN_COMPLETE, taskState, data);
    }

    public void failRun(String runCode, String conversationCode, String errorMessage) {
        emit(runCode, conversationCode, RunStreamEventTypeEnum.RUN_ERROR,
                RunState_FAILED_CODE, errorMessage);
    }

    public void cancelRun(String runCode, String conversationCode) {
        emit(runCode, conversationCode, RunStreamEventTypeEnum.RUN_CANCELLED,
                RunState_CANCELLED_CODE, null);
    }

    private static final String RunState_FAILED_CODE = "failed";
    private static final String RunState_CANCELLED_CODE = "cancelled";
}
