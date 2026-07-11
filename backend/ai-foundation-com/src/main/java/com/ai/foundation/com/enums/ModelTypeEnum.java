package com.ai.foundation.com.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ModelTypeEnum {

    CHAT("CHAT", "对话模型"),
    EMBEDDING("EMBEDDING", "向量模型");

    private final String code;
    private final String desc;

    public static ModelTypeEnum of(String code) {
        for (ModelTypeEnum t : values()) {
            if (t.code.equalsIgnoreCase(code)) {
                return t;
            }
        }
        throw new IllegalArgumentException("不支持的模型类型: " + code);
    }
}
