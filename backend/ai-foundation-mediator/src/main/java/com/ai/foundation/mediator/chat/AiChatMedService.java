package com.ai.foundation.mediator.chat;

import com.ai.foundation.biz.conversation.AgentMessageService;
import com.ai.foundation.biz.project.AgentProjectService;
import com.ai.foundation.com.enums.ChatStreamEventTypeEnum;
import com.ai.foundation.com.exception.BusinessException;
import com.ai.foundation.com.response.ResultCode;
import com.ai.foundation.dal.entity.AgentConversationInfo;
import com.ai.foundation.dal.entity.AgentMessageInfo;
import com.ai.foundation.dal.entity.AgentProject;
import com.ai.foundation.facade.dto.chat.ChatStreamChunkDTO;
import com.ai.foundation.facade.dto.chat.ChatSyncRequest;
import com.ai.foundation.facade.dto.chat.ChatSyncResponse;
import com.ai.foundation.facade.dto.chat.ChatStreamRequest;
import com.ai.foundation.mediator.conversation.AgentConversationMedService;
import com.ai.foundation.mediator.model.AgentModelResolver;
import com.ai.foundation.mediator.prompt.ProjectPromptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatMedService {

    private static final String DEFAULT_SYSTEM_PROMPT = "你是一个智能助手，请简洁、专业地回答用户问题。";

    private final ChatClient.Builder chatClientBuilder;
    private final AgentConversationMedService conversationMedService;
    private final AgentMessageService messageService;
    private final AgentModelResolver modelResolver;
    private final ChatHistoryComposer chatHistoryComposer;
    private final AgentProjectService projectService;
    private final ProjectPromptService projectPromptService;
    private final ProjectSystemPromptAdvisor projectSystemPromptAdvisor;

    // ========================= 同步 Chat =========================

    public ChatSyncResponse syncChat(ChatSyncRequest request, String clientIp) {
        validateRequest(request.getUserMessage());
        AgentConversationInfo conversation = conversationMedService.requireByCode(request.getConversationCode());
        String modelName = resolveModelName(request.getModelName(), conversation);

        AgentMessageInfo userMsg = saveUserMessage(conversation.getId(), request.getUserMessage(), clientIp);
        ChatClient chatClient = buildChatClient(modelName, request.getTemperature(), request.getMaxTokens());
        String systemPrompt = buildSystemPrompt(conversation, request.getSystemPrompt());
        List<Message> messages = buildMessages(conversation, request.getUserMessage());

        long startTime = System.currentTimeMillis();
        String content;
        try {
            content = callModel(modelName, chatClient, messages, systemPrompt);
        } catch (Exception e) {
            log.error("同步对话调用模型失败 conversationCode={}", request.getConversationCode(), e);
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "模型调用失败: " + e.getMessage());
        }
        long duration = System.currentTimeMillis() - startTime;

        AgentMessageInfo assistantMsg = saveAssistantMessage(conversation.getId(), content, 0, duration);
        conversationMedService.touchLastMessage(conversation.getId(), modelName);
        chatHistoryComposer.completeTurn(conversation.getId(), request.getUserMessage(), content, modelName);
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
        String systemPrompt = buildSystemPrompt(conversation, request.getSystemPrompt());
        List<Message> messages = buildMessages(conversation, request.getUserMessage());

        StringBuilder contentBuilder = new StringBuilder();

        return Flux.concat(
                Flux.just(ChatStreamChunkDTO.of(ChatStreamEventTypeEnum.START)),
                streamModel(modelName, chatClient, messages, systemPrompt)
                        .filter(StringUtils::isNotEmpty)
                        .doOnNext(contentBuilder::append)
                        .map(token -> ChatStreamChunkDTO.of(ChatStreamEventTypeEnum.TOKEN, token)),
                Flux.defer(() -> {
                    long duration = System.currentTimeMillis();
                    saveAssistantMessage(conversation.getId(), contentBuilder.toString(), 0, duration);
                    conversationMedService.touchLastMessage(conversation.getId(), modelName);
                    chatHistoryComposer.completeTurn(conversation.getId(),
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
        String resolvedSystemPrompt = buildSystemPrompt(conversation, systemPrompt);
        List<Message> messages = buildMessages(conversation, userMessage);
       return streamModel(resolvedModel, chatClient, messages, resolvedSystemPrompt)
                .filter(StringUtils::isNotEmpty);
   }

    /**
     * 流式获取 chunk（reasoning + content 分离），不持久化消息。
     *
     * <p>使用 {@code chatResponse()} 而非 {@code content()}，以提取
     * reasoning_content（思考链）与正文 content，分别推送给前端。
     *
     * @param conversation  会话
     * @param userMessage   用户消息
     * @param systemPrompt   系统提示词（可选）
     * @param modelName       模型名称（可选）
     * @return 分离后的 chunk 流
     */
    public Flux<ChatStreamChunk> streamChunks(AgentConversationInfo conversation, String userMessage,
                                                String systemPrompt, String modelName) {
        String resolvedModel = resolveModelName(modelName, conversation);
        ChatClient chatClient = buildChatClient(resolvedModel, null, null);
        String resolvedSystemPrompt = buildSystemPrompt(conversation, systemPrompt);
        List<Message> messages = buildMessages(conversation, userMessage);
        return chatClient.prompt()
                .messages(messages)
                .advisors(advisor -> advisor
                        .param(ProjectSystemPromptAdvisor.SYSTEM_PROMPT_CONTEXT_KEY, resolvedSystemPrompt)
                        .advisors(projectSystemPromptAdvisor))
                .stream()
                .chatResponse()
                .map(this::toChunk)
                .doOnError(e -> log.error("streamChunks 失败 model={} msg={}", resolvedModel, extractErrorMsg(e)));
    }

    /**
     * 构建实际发送给模型的消息栈，用于 Run Inspector 调试展示。
     *
     * @param conversation 会话
     * @param userMessage 用户消息
     * @param systemPrompt 系统提示词（可选）
     * @return 模型请求消息栈
     */
    public List<ChatRequestMessage> buildRequestMessages(AgentConversationInfo conversation, String userMessage,
                                                          String systemPrompt) {
        String resolvedSystemPrompt = buildSystemPrompt(conversation, systemPrompt);
        List<Message> messages = new ArrayList<>();
        if (StringUtils.isNotBlank(resolvedSystemPrompt)) {
            messages.add(new SystemMessage(resolvedSystemPrompt));
        }
        messages.addAll(buildMessages(conversation, userMessage));
        return messages.stream()
                .map(this::toRequestMessage)
                .toList();
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

    private String buildSystemPrompt(AgentConversationInfo conversation, String requestSystemPrompt) {
        AgentProject project = projectService.getById(conversation.getProjectId());
        String summaryBlock = chatHistoryComposer.getSummaryBlock(conversation);
        return projectPromptService.buildSystemPrompt(project, conversation,
                summaryBlock, requestSystemPrompt, DEFAULT_SYSTEM_PROMPT);
    }

    /**
     * 构建历史消息列表：从热缓存取最近 N 轮对话的原文。
     * 当前用户消息（本次请求的 userMessage）会追加到末尾。
     */
    private List<Message> buildMessages(AgentConversationInfo conversation, String userMessage) {
        List<ChatHistoryComposer.HotTurn> hotTurns = chatHistoryComposer.composeHistory(conversation);

        List<Message> messages = new ArrayList<>();
        for (ChatHistoryComposer.HotTurn turn : hotTurns) {
            if (StringUtils.isNotBlank(turn.getUser())) {
                messages.add(new UserMessage(turn.getUser()));
            }
            if (StringUtils.isNotBlank(turn.getAssistant())) {
                messages.add(new AssistantMessage(turn.getAssistant()));
            }
        }
        if (StringUtils.isNotBlank(userMessage)) {
            Message last = messages.isEmpty() ? null : messages.get(messages.size() - 1);
            boolean duplicateUser = last instanceof UserMessage
                    && userMessage.equals(((UserMessage) last).getText());
            if (!duplicateUser) {
                messages.add(new UserMessage(userMessage));
            }
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

    private String callModel(String modelName, ChatClient chatClient, List<Message> messages, String systemPrompt) {
        try {
            return chatClient.prompt()
                .messages(messages)
                .advisors(advisor -> advisor
                        .param(ProjectSystemPromptAdvisor.SYSTEM_PROMPT_CONTEXT_KEY, systemPrompt)
                        .advisors(projectSystemPromptAdvisor))
                .call()
                .content();
        } catch (Exception e) {
            log.error("callModel 失败 model={} msg={}", modelName, extractErrorMsg(e));
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "模型调用失败: " + e.getMessage());
        }
    }

    private Flux<String> streamModel(String modelName, ChatClient chatClient,
                                     List<Message> messages, String systemPrompt) {
        return chatClient.prompt()
                .messages(messages)
                .advisors(advisor -> advisor
                        .param(ProjectSystemPromptAdvisor.SYSTEM_PROMPT_CONTEXT_KEY, systemPrompt)
                        .advisors(projectSystemPromptAdvisor))
                .stream()
                .content()
                .doOnError(e -> log.error("streamModel 失败 model={} msg={}", modelName, extractErrorMsg(e)));
    }

    private String extractErrorMsg(Throwable e) {
        Throwable cause = e.getCause();
        if (cause != null && cause.getMessage() != null) {
            return cause.getClass().getSimpleName() + ": " + cause.getMessage();
        }
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }

    private ChatRequestMessage toRequestMessage(Message message) {
        String role = message.getMessageType().name().toLowerCase(Locale.ROOT);
        return new ChatRequestMessage(role, message.getText());
    }

    /**
     * 将 Spring AI 的 {@link ChatResponse} 转换为 {@link ChatStreamChunk}，
     * 分离 reasoning_content（思考链）与正文 content。
     */
    private ChatStreamChunk toChunk(ChatResponse response) {
        if (response == null || response.getResult() == null) {
            return new ChatStreamChunk(null, null);
        }
        Generation gen = response.getResult();
        String content = gen.getOutput() != null ? gen.getOutput().getText() : null;
        String reasoning = null;
        if (gen.getMetadata() != null) {
            Object rc = gen.getMetadata().get("reasoningContent");
            if (rc instanceof String s && !s.isEmpty()) {
                reasoning = s;
            }
        }
        return new ChatStreamChunk(reasoning, content);
    }
}
