package com.ai.foundation.mediator.chat;

import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 将项目维度系统提示词注入到 Spring AI 请求上下文。
 */
@Component
public class ProjectSystemPromptAdvisor implements CallAdvisor, StreamAdvisor {

    public static final String SYSTEM_PROMPT_CONTEXT_KEY = "projectSystemPrompt";

    private static final int ORDER = 0;

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        return chain.nextCall(applySystemPrompt(request));
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        return chain.nextStream(applySystemPrompt(request));
    }

    private ChatClientRequest applySystemPrompt(ChatClientRequest request) {
        Object promptValue = request.context().get(SYSTEM_PROMPT_CONTEXT_KEY);
        String systemPrompt = promptValue instanceof String text ? text : null;
        if (StringUtils.isBlank(systemPrompt)) {
            return request;
        }
        return request.mutate()
                .prompt(request.prompt().augmentSystemMessage(existing -> merge(existing, systemPrompt)))
                .build();
    }

    private SystemMessage merge(SystemMessage existing, String prompt) {
        String existingText = existing == null ? null : existing.getText();
        if (StringUtils.isBlank(existingText)) {
            return new SystemMessage(prompt.trim());
        }
        return new SystemMessage(existingText.trim() + "\n\n" + prompt.trim());
    }
}
