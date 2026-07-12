package com.ai.foundation.mediator.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Kimi K2.x 直连客户端，绕过 Spring AI 1.1.x 对 Moonshot 不兼容的请求字段。
 */
@Slf4j
@Component
public class MoonshotChatClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public MoonshotChatClient(@Value("${spring.ai.openai.api-key}") String apiKey,
                              @Value("${spring.ai.openai.base-url}") String baseUrl,
                              ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(trimTrailingSlash(baseUrl))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public Flux<String> stream(String model, List<Message> messages) {
        Map<String, Object> body = buildRequestBody(model, messages, true);
        AtomicReference<StringBuilder> lineBuffer = new AtomicReference<>(new StringBuilder());
        return webClient.post()
                .uri("/v1/chat/completions")
                .bodyValue(body)
                .exchangeToFlux(response -> {
                    if (response.statusCode().isError()) {
                        return response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMapMany(errorBody -> Flux.error(new IllegalStateException(
                                        formatApiError(response.statusCode().value(), errorBody))));
                    }
                    AtomicReference<StringBuilder> pending = lineBuffer;
                    return response.bodyToFlux(DataBuffer.class)
                            .concatMap(buffer -> {
                                try {
                                    String chunk = buffer.toString(StandardCharsets.UTF_8);
                                    StringBuilder sb = pending.get();
                                    sb.append(chunk);
                                    return Flux.fromIterable(drainSseLines(sb));
                                } finally {
                                    DataBufferUtils.release(buffer);
                                }
                            })
                            .concatWith(Flux.defer(() -> {
                                StringBuilder sb = pending.get();
                                if (sb.isEmpty()) {
                                    return Flux.empty();
                                }
                                List<String> tokens = extractTokensFromLine(sb.toString().trim());
                                sb.setLength(0);
                                return Flux.fromIterable(tokens);
                            }));
                })
                .filter(StringUtils::isNotEmpty);
    }

    public String call(String model, List<Message> messages) {
        Map<String, Object> body = buildRequestBody(model, messages, false);
        String responseBody = webClient.post()
                .uri("/v1/chat/completions")
                .bodyValue(body)
                .retrieve()
                .onStatus(status -> status.isError(), response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(errorBody -> Mono.error(new IllegalStateException(
                                formatApiError(response.statusCode().value(), errorBody)))))
                .bodyToMono(String.class)
                .block();
        return extractContent(responseBody);
    }

    private Map<String, Object> buildRequestBody(String model, List<Message> messages, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", toPayloadMessages(messages));
        body.put("stream", stream);
        body.put("thinking", Map.of("type", "disabled"));
        return body;
    }

    private List<Map<String, String>> toPayloadMessages(List<Message> messages) {
        List<Map<String, String>> payload = new ArrayList<>();
        for (Message message : messages) {
            String role = resolveRole(message);
            if (role == null) {
                continue;
            }
            payload.add(Map.of("role", role, "content", message.getText()));
        }
        return payload;
    }

    private String resolveRole(Message message) {
        if (message instanceof SystemMessage) {
            return "system";
        }
        if (message instanceof UserMessage) {
            return "user";
        }
        if (message instanceof AssistantMessage) {
            return "assistant";
        }
        return null;
    }

    private List<String> drainSseLines(StringBuilder pending) {
        List<String> tokens = new ArrayList<>();
        int newlineIdx;
        while ((newlineIdx = indexOfLineSeparator(pending)) >= 0) {
            String line = pending.substring(0, newlineIdx);
            pending.delete(0, newlineIdx + 1);
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            tokens.addAll(extractTokensFromLine(line.trim()));
        }
        return tokens;
    }

    private List<String> extractTokensFromLine(String line) {
        List<String> tokens = new ArrayList<>();
        if (!line.startsWith("data:")) {
            return tokens;
        }
        String data = line.substring(5).trim();
        if (data.isEmpty() || "[DONE]".equals(data)) {
            return tokens;
        }
        try {
            JsonNode root = objectMapper.readTree(data);
            JsonNode content = root.path("choices").path(0).path("delta").path("content");
            if (content.isTextual()) {
                String text = content.asText();
                if (StringUtils.isNotEmpty(text)) {
                    tokens.add(text);
                }
            }
        } catch (Exception e) {
            log.warn("无法解析 Moonshot SSE 片段: {}", data, e);
        }
        return tokens;
    }

    private static int indexOfLineSeparator(StringBuilder sb) {
        for (int i = 0; i < sb.length(); i++) {
            if (sb.charAt(i) == '\n') {
                return i;
            }
        }
        return -1;
    }

    private String extractContent(String responseBody) {
        if (StringUtils.isBlank(responseBody)) {
            return "";
        }
        try {
            JsonNode content = objectMapper.readTree(responseBody)
                    .path("choices").path(0).path("message").path("content");
            return content.isTextual() ? content.asText() : "";
        } catch (Exception e) {
            throw new IllegalStateException("解析 Moonshot 响应失败: " + e.getMessage(), e);
        }
    }

    private String formatApiError(int status, String errorBody) {
        if (StringUtils.isBlank(errorBody)) {
            return status + " Bad Request from Moonshot";
        }
        try {
            JsonNode error = objectMapper.readTree(errorBody).path("error");
            if (error.isObject()) {
                String message = error.path("message").asText(errorBody);
                return status + " Bad Request from Moonshot: " + message;
            }
        } catch (Exception ignored) {
            /* fall through */
        }
        return status + " Bad Request from Moonshot: " + errorBody;
    }

    private static String trimTrailingSlash(String baseUrl) {
        if (baseUrl == null) {
            return "";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
