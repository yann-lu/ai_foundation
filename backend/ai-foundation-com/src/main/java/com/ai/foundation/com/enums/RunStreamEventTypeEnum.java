package com.ai.foundation.com.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RunStreamEventTypeEnum {

    RUN_START("run_start"),
    CHAT_START("chat_start"),
    CHAT_TOKEN("chat_token"),
    CHAT_COMPLETE("chat_complete"),
    RUN_COMPLETE("run_complete"),
    RUN_ERROR("run_error"),
    RUN_CANCELLED("run_cancelled");

    private final String code;
}
