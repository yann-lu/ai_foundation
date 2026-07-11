package com.ai.foundation.com.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RunStateEnum {

    CREATED("created"),
    EXECUTING("executing"),
    COMPLETED("completed"),
    FAILED("failed"),
    CANCELLED("cancelled");

    private final String code;
}
