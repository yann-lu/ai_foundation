package com.ai.foundation.mediator.chat;

import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.Map;

/**
 * Kimi K2.x 模型与 OpenAI 兼容客户端的参数适配。
 *
 * <p>K2 系列对 temperature / top_p 等采样参数有固定值约束，传入其它值会 400；
 * 默认思考模式还要求多轮 assistant 消息携带 reasoning_content，普通对话需显式关闭。
 */
final class KimiChatOptionsHelper {

    private KimiChatOptionsHelper() {
    }

    static OpenAiChatOptions build(String modelName, Double temperature, Integer maxTokens) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder().model(modelName);
        if (isKimiK2Model(modelName)) {
            builder.extraBody(Map.of("thinking", Map.of("type", "disabled")));
        } else if (temperature != null) {
            builder.temperature(temperature);
        }
        if (maxTokens != null) {
            builder.maxTokens(maxTokens);
        }
        return builder.build();
    }

    static boolean isKimiK2Model(String modelName) {
        if (StringUtils.isBlank(modelName)) {
            return false;
        }
        String normalized = modelName.trim().toLowerCase();
        return normalized.startsWith("kimi-k2");
    }
}
