package com.ai.foundation.gateway.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Component;

/**
 * Clears the default {@code temperature} that Spring AI auto-config injects (0.7).
 *
 * <p>Moonshot's Kimi K2 models only accept a single fixed temperature value that
 * changes without notice (observed alternating between 0.6 and 1). Sending any
 * other value — including the auto-config default — triggers a 400. By nulling
 * the default temperature, the field is omitted from the API request entirely
 * (thanks to {@code @JsonInclude(NON_NULL)} on {@code ChatCompletionRequest}),
 * letting Moonshot apply its own default.
 *
 * <p>Runtime temperature provided by callers for non-Kimi models still works
 * normally, because {@code ModelOptionsUtils.merge} copies the runtime value
 * when it is non-null.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiChatOptionsInitializer {

    private final OpenAiChatModel chatModel;
    private final OpenAiChatProperties chatProperties;

    @PostConstruct
    public void clearDefaultTemperature() {
        Double previous = chatProperties.getOptions().getTemperature();
        chatProperties.getOptions().setTemperature(null);
        log.info("Cleared default OpenAiChatOptions.temperature (was {})", previous);
    }
}
