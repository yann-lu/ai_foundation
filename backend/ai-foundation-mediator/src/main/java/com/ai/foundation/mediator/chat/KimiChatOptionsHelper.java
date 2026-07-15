package com.ai.foundation.mediator.chat;

import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.openai.OpenAiChatOptions;

/**
 * Kimi K2.x 模型与 OpenAI 兼容客户端的参数适配。
 *
 * <p>K2 系列对 temperature 有固定值约束（仅允许单一值，且该值不稳定，
 * 在 0.6 与 1 之间反复变化）。传入任何不匹配的值都会导致 400。
 * 因此对 Kimi K2 模型<b>不发送 temperature</b>，交由 Moonshot 使用服务端默认值。
 *
 * <p>默认 temperature 的清零由 {@code OpenAiChatOptionsInitializer} 负责，
 * thinking 模式关闭由配置文件 {@code spring.ai.openai.chat.options.extra-body} 控制。
 */
final class KimiChatOptionsHelper {

    private KimiChatOptionsHelper() {
    }

    static OpenAiChatOptions build(String modelName, Double temperature, Integer maxTokens) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder().model(modelName);
        if (!isKimiK2Model(modelName) && temperature != null) {
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
