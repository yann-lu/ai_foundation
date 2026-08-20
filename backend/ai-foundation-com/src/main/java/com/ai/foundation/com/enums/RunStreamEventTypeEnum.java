package com.ai.foundation.com.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RunStreamEventTypeEnum {

    RUN_START("run_start"),
    CHAT_START("chat_start"),
    REQUEST_MESSAGES("request_messages"),
    USER_MESSAGE("user_message"),
    CHAT_REASONING("chat_reasoning"),
    CHAT_TOKEN("chat_token"),
    CHAT_COMPLETE("chat_complete"),
    SUMMARY_UPDATE("summary_update"),
    TOOL_CALL("tool_call"),
    TOOL_RESULT("tool_result"),
    TOOL_STATUS("tool_status"),
    RUN_COMPLETE("run_complete"),
    RUN_ERROR("run_error"),
    RUN_CANCELLED("run_cancelled");

    private final String code;
}
