package com.ai.foundation.mediator.chat;

import com.ai.foundation.biz.conversation.AgentConversationService;
import com.ai.foundation.mediator.model.AgentModelResolver;
import com.ai.foundation.dal.entity.AgentConversationInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 会话滚动摘要服务。
 *
 * <p>每轮对话完成后，根据旧摘要 + 本轮 user/assistant 交互，
 * 调用 LLM 增量生成更新后的摘要，存入 conversation.summary 字段。
 * 摘要用于在后续对话中注入 system prompt，压缩长期历史上下文。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationSummaryService {

    private static final int MAX_SUMMARY_CHARS = 800;

    private static final String SUMMARY_SYSTEM_PROMPT =
            "你是会话摘要助手。根据旧摘要与本轮对话，输出更新后的简洁中文摘要。"
            + "保留：用户长期诉求、已确认参数（酒店/日期/产品等）、进行中任务、关键结论。"
            + "删除：无关闲聊与重复信息。只输出摘要正文，不超过300字。";

    private final ChatClient.Builder chatClientBuilder;
    private final AgentConversationService conversationService;
    private final AgentModelResolver modelResolver;
    private final MoonshotChatClient moonshotChatClient;

    /**
     * 根据本轮对话增量更新会话摘要。
     *
     * @param conversationId  会话主键
     * @param userMessage      本轮用户输入
     * @param assistantReply   本轮助手回复
     * @param modelName        模型名称（可选，为空时从会话解析）
     */
    public void updateSummary(Long conversationId, String userMessage,
                              String assistantReply, String modelName) {
        if (conversationId == null
                || StringUtils.isBlank(userMessage)
                || StringUtils.isBlank(assistantReply)) {
            return;
        }
        try {
            AgentConversationInfo conversation = conversationService.getById(conversationId);
            if (conversation == null) {
                return;
            }

            String summaryModel = resolveSummaryModel(modelName, conversation);
            ChatClient chatClient = chatClientBuilder
                    .defaultOptions(KimiChatOptionsHelper.build(summaryModel, null, null))
                    .build();

            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(SUMMARY_SYSTEM_PROMPT));
            messages.add(new UserMessage(buildSummaryUserPrompt(
                    conversation.getSummary(), userMessage.trim(), assistantReply.trim())));

            String content = KimiChatOptionsHelper.isKimiK2Model(summaryModel)
                    ? moonshotChatClient.call(summaryModel, messages)
                    : chatClient.prompt()
                            .messages(messages)
                            .call()
                            .content();

            if (StringUtils.isBlank(content)) {
                return;
            }

            String summary = truncateSummary(content.trim());
            AgentConversationInfo update = new AgentConversationInfo();
            update.setId(conversationId);
            update.setSummary(summary);
            conversationService.updateById(update);

            log.info("会话摘要更新成功 conversationId={} summaryLength={}",
                    conversationId, summary.length());
        } catch (Exception e) {
            log.warn("会话摘要更新失败 conversationId={} msg={}",
                    conversationId, e.getMessage());
        }
    }

    /**
     * 构建摘要【会话摘要】块，供 system prompt 注入。
     *
     * @param summary 会话摘要
     * @return 格式化后的摘要块，无摘要时返回 null
     */
    public String formatSummaryBlock(String summary) {
        if (StringUtils.isBlank(summary)) {
            return null;
        }
        return "【会话摘要】\n" + summary.trim()
                + "\n请结合摘要理解指代与延续诉求；摘要未提及的信息不要臆造。";
    }

    private String resolveSummaryModel(String preferred, AgentConversationInfo conversation) {
        if (StringUtils.isNotBlank(preferred)) {
            return preferred.trim();
        }
        if (StringUtils.isNotBlank(conversation.getModelName())) {
            return conversation.getModelName();
        }
        return modelResolver.resolveChatModel(conversation.getProjectId());
    }

    private String buildSummaryUserPrompt(String oldSummary, String userMessage, String assistantReply) {
        StringBuilder sb = new StringBuilder();
        sb.append("旧摘要：").append(StringUtils.defaultIfBlank(oldSummary, "无")).append('\n');
        sb.append("本轮用户：").append(userMessage).append('\n');
        sb.append("本轮助手：").append(assistantReply);
        return sb.toString();
    }

    private String truncateSummary(String summary) {
        if (summary.length() <= MAX_SUMMARY_CHARS) {
            return summary;
        }
        return summary.substring(0, MAX_SUMMARY_CHARS) + "…";
    }
}
