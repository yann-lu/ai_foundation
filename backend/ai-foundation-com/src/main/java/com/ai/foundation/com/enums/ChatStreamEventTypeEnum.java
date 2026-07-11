package com.ai.foundation.com.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ChatStreamEventTypeEnum {

    START("start"),
    TOKEN("token"),
    COMPLETE("complete"),
    ERROR("error");

    private final String code;
}
