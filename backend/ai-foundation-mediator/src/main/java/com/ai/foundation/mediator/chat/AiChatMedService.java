package com.ai.foundation.mediator.chat;

import com.ai.foundation.biz.conversation.AgentMessageService;
import com.ai.foundation.com.enums.ChatStreamEventTypeEnum;
import com.ai.foundation.com.exception.BusinessException;
import com.ai.foundation.com.response.ResultCode;
import com.ai.foundation.dal.entity.AgentConversationInfo;
import com.ai.foundation.dal.entity.AgentMessageInfo;
import com.ai.foundation.facade.dto.chat.ChatStreamChunkDTO;
import com.ai.foundation.facade.dto.chat.ChatSyncRequest;
import com.ai.foundation.facade.dto.chat.ChatSyncResponse;
import com.ai.foundation.facade.dto.chat.ChatStreamRequest;
import com.ai.foundation.mediator.conversation.AgentConversationMedService;
import com.ai.foundation.mediator.model.AgentModelResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatMedService {

    private static final int HISTORY_LIMIT = 20;
    private static final String DEFAULT_SYSTEM_PROMPT = "你是一个智能助手，请简洁、专业地回答用户问题。";

    private final ChatClient.Builder chatClientBuilder;
    private final AgentConversationMedService conversationMedService;
    private final AgentMessageService messageService;
    private final AgentModelResolver modelResolver;
    private final ObjectMapper objectMapper;
    private final ConversationSummaryService summaryService;
    private final MoonshotChatClient moonshotChatClient;

    // ========================= 同步 Chat =========================

    public ChatSyncResponse syncChat(ChatSyncRequest request, String clientIp) {
        validateRequest(request.getUserMessage());
        AgentConversationInfo conversation = conversationMedService.requireByCode(request.getConversationCode());
        String modelName = resolveModelName(request.getModelName(), conversation);

        AgentMessageInfo userMsg = saveUserMessage(conversation.getId(), request.getUserMessage(), clientIp);
        ChatClient chatClient = buildChatClient(modelName, request.getTemperature(), request.getMaxTokens());
        List<Message> messages = buildMessages(conversation, request.getSystemPrompt(), request.getUserMessage());

        long startTime = System.currentTimeMillis();
        String content;
        try {
            content = callModel(modelName, chatClient, messages);
        } catch (Exception e) {
            log.error("同步对话调用模型失败 conversationCode={}", request.getConversationCode(), e);
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "模型调用失败: " + e.getMessage());
        }
        long duration = System.currentTimeMillis() - startTime;

        AgentMessageInfo assistantMsg = saveAssistantMessage(conversation.getId(), content, 0, duration);
        conversationMedService.touchLastMessage(conversation.getId(), modelName);
        summaryService.updateSummary(conversation.getId(), request.getUserMessage(), content, modelName);
        log.info("同步对话成功 conv={} userMsgId={} assistantMsgId={} duration={}ms",
                conversation.getConversationCode(), userMsg.getId(), assistantMsg.getId(), duration);
        return new ChatSyncResponse(content, 0, duration, assistantMsg.getId());
    }

    // ========================= 流式 Chat =========================

    public Flux<ChatStreamChunkDTO> streamChat(ChatStreamRequest request, String clientIp) {
        validateRequest(request.getUserMessage());
        AgentConversationInfo conversation = conversationMedService.requireByCode(request.getConversationCode());
        String modelName = resolveModelName(request.getModelName(), conversation);

        saveUserMessage(conversation.getId(), request.getUserMessage(), clientIp);
        ChatClient chatClient = buildChatClient(modelName, request.getTemperature(), request.getMaxTokens());
        List<Message> messages = buildMessages(conversation, request.getSystemPrompt(), request.getUserMessage());

        StringBuilder contentBuilder = new StringBuilder();

        return Flux.concat(
                Flux.just(ChatStreamChunkDTO.of(ChatStreamEventTypeEnum.START)),
                streamModel(modelName, chatClient, messages)
                        .filter(StringUtils::isNotBlank)
                        .doOnNext(contentBuilder::append)
                        .map(token -> ChatStreamChunkDTO.of(ChatStreamEventTypeEnum.TOKEN, token)),
                Flux.defer(() -> {
                    long duration = System.currentTimeMillis();
                    saveAssistantMessage(conversation.getId(), contentBuilder.toString(), 0, duration);
                    conversationMedService.touchLastMessage(conversation.getId(), modelName);
                    summaryService.updateSummary(conversation.getId(),
                            request.getUserMessage(), contentBuilder.toString(), modelName);
                    return Flux.just(ChatStreamChunkDTO.of(ChatStreamEventTypeEnum.COMPLETE));
                })
        ).doOnSubscribe(sub -> log.info("流式对话开始 conv={}", request.getConversationCode()))
         .doOnComplete(() -> log.info("流式对话完成 conv={}", request.getConversationCode()))
         .onErrorResume(ex -> {
             log.error("流式对话异常 conv={}", request.getConversationCode(), ex);
             return Flux.just(ChatStreamChunkDTO.of(ChatStreamEventTypeEnum.ERROR, ex.getMessage()));
         });
    }

    // ========================= Orchestrator 专用 =========================

    /**
     * 流式获取 token（不持久化消息，由 AgentRunMedService 负责保存）。
     *
     * @param conversation  会话
     * @param userMessage   用户消息
     * @param systemPrompt  系统提示词（可选）
     * @param modelName     模型名称（可选，为空时从会话解析）
     * @return token 流
     */
    public Flux<String> streamTokens(AgentConversationInfo conversation, String userMessage,
                                     String systemPrompt, String modelName) {
        String resolvedModel = resolveModelName(modelName, conversation);
        ChatClient chatClient = buildChatClient(resolvedModel, null, null);
        List<Message> messages = buildMessages(conversation, systemPrompt, userMessage);
        return streamModel(resolvedModel, chatClient, messages)
                .filter(StringUtils::isNotBlank);
    }

    private String callModel(String modelName, ChatClient chatClient, List<Message> messages) {
        if (KimiChatOptionsHelper.isKimiK2Model(modelName)) {
            return moonshotChatClient.call(modelName, messages);
        }
        return chatClient.prompt()
                .messages(messages)
                .call()
                .content();
    }

    private Flux<String> streamModel(String modelName, ChatClient chatClient, List<Message> messages) {
        if (KimiChatOptionsHelper.isKimiK2Model(modelName)) {
            return moonshotChatClient.stream(modelName, messages)
                    .switchIfEmpty(Flux.defer(() -> fallbackKimiSyncStream(modelName, messages)))
                    .onErrorResume(ex -> {
                        log.warn("Kimi 流式调用失败，回退同步 model={} err={}", modelName, ex.getMessage());
                        return fallbackKimiSyncStream(modelName, messages);
                    });
        }
        return chatClient.prompt()
                .messages(messages)
                .stream()
                .content();
    }

    private Flux<String> fallbackKimiSyncStream(String modelName, List<Message> messages) {
        return Mono.fromCallable(() -> moonshotChatClient.call(modelName, messages))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(content -> StringUtils.isBlank(content) ? Flux.empty() : Flux.just(content));
    }

    /**
     * 保存助手消息并更新会话最后消息时间。
     *
     * @param conversation  会话
     * @param content       助手回复内容
     * @param durationMs    耗时（毫秒）
     * @return 保存后的消息实体
     */
    public AgentMessageInfo saveAssistantMessage(AgentConversationInfo conversation, String content, long durationMs) {
        AgentMessageInfo msg = saveAssistantMessage(conversation.getId(), content, 0, durationMs);
        conversationMedService.touchLastMessage(conversation.getId(), conversation.getModelName());
        return msg;
    }

    // ========================= 内部方法 =========================

    private void validateRequest(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "用户消息不能为空");
        }
    }

    private ChatClient buildChatClient(String modelName, Double temperature, Integer maxTokens) {
        return chatClientBuilder
                .defaultOptions(KimiChatOptionsHelper.build(modelName, temperature, maxTokens))
                .build();
    }

    private String resolveModelName(String requestModelName, AgentConversationInfo conversation) {
        if (requestModelName != null && !requestModelName.isBlank()) {
            return requestModelName;
        }
        if (conversation.getModelName() != null && !conversation.getModelName().isBlank()) {
            return conversation.getModelName();
        }
        return modelResolver.resolveChatModel(conversation.getProjectId());
    }

    private List<Message> buildMessages(AgentConversationInfo conversation, String systemPrompt, String userMessage) {
        List<AgentMessageInfo> history = messageService.recentMessages(conversation.getId(), HISTORY_LIMIT);
        Collections.reverse(history);

        List<Message> messages = new ArrayList<>();
        String sys = (systemPrompt != null && !systemPrompt.isBlank()) ? systemPrompt : DEFAULT_SYSTEM_PROMPT;
        String summaryBlock = summaryService.formatSummaryBlock(conversation.getSummary());
        if (summaryBlock != null) {
            sys = summaryBlock + "\n\n" + sys;
        }
        messages.add(new SystemMessage(sys));

        for (AgentMessageInfo msg : history) {
            if ("user".equals(msg.getRole())) {
                messages.add(new UserMessage(msg.getContent()));
            } else if ("assistant".equals(msg.getRole())) {
                messages.add(new AssistantMessage(msg.getContent()));
            }
        }
        AgentMessageInfo last = history.isEmpty() ? null : history.get(history.size() - 1);
        boolean duplicateUser = last != null && "user".equals(last.getRole())
                && userMessage.equals(last.getContent());
        if (!duplicateUser) {
            messages.add(new UserMessage(userMessage));
        }
        return messages;
    }

    private AgentMessageInfo saveUserMessage(Long conversationId, String content, String clientIp) {
        AgentMessageInfo msg = new AgentMessageInfo();
        msg.setConversationId(conversationId);
        msg.setRole("user");
        msg.setContent(content);
        msg.setClientIp(clientIp != null ? clientIp : "");
        msg.setState(1);
        messageService.save(msg);
        return msg;
    }

    private AgentMessageInfo saveAssistantMessage(Long conversationId, String content, int tokenCount, long durationMs) {
        AgentMessageInfo msg = new AgentMessageInfo();
        msg.setConversationId(conversationId);
        msg.setRole("assistant");
        msg.setContent(content);
        msg.setTokenCount(tokenCount);
        msg.setDurationMs((int) durationMs);
        msg.setState(1);
        messageService.save(msg);
        return msg;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
