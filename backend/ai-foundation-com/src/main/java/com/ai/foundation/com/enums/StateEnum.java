package com.ai.foundation.com.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum StateEnum {

    DISABLED(0, "停用"),
    ENABLED(1, "启用");

    private final int code;
    private final String desc;

    public boolean enabled() {
        return this == ENABLED;
    }

    public static StateEnum of(Integer code) {
        if (code == null) {
            return ENABLED;
        }
        for (StateEnum s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        return ENABLED;
    }
}
