package com.ai.foundation.mediator.chat;

import com.ai.foundation.biz.conversation.AgentConversationService;
import com.ai.foundation.biz.conversation.AgentMessageService;
import com.ai.foundation.com.constant.RedisKeyConstants;
import com.ai.foundation.dal.entity.AgentConversationInfo;
import com.ai.foundation.dal.entity.AgentMessageInfo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 聊天历史组装器：滚动增量摘要 + 热缓存。
 *
 * <h3>核心机制</h3>
 * <ol>
 *   <li>每完成一轮对话（用户消息 + 助手回复），将该轮推入 Redis 热缓存。</li>
 *   <li>当热缓存超过 {@code hotTurns} 轮时，最老的一轮被挤出，并异步调用 LLM 做增量摘要更新。</li>
 *   <li>组装对话历史时：最近 {@code hotTurns} 轮用原文，更早的用滚动摘要替代，注入到 system prompt。</li>
 * </ol>
 *
 * <h3>热缓存结构</h3>
 * <p>Redis List，每个元素是一轮对话的 JSON：{@code {"user":"...","assistant":"..."}}。
 * List 头部（index 0）是最老的一轮，尾部是最新的一轮。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatHistoryComposer {

    private static final TypeReference<HotTurn> HOT_TURN_TYPE = new TypeReference<>() {};

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AgentConversationProperties conversationProperties;
    private final AgentConversationService conversationService;
    private final AgentMessageService messageService;
    private final ConversationSummaryService summaryService;
    private final SummaryAsyncService summaryAsyncService;

    /**
     * 一轮对话（用户消息 + 助手回复），用于热缓存存储。
     */
    @lombok.Data
    public static class HotTurn {
        private String user;
        private String assistant;
    }

    /**
     * 组装发送给模型的消息列表：摘要（system 上下文） + 热缓存中的最近 N 轮原文。
     *
     * <p>当前消息（即本次用户刚发的消息）不包含在返回结果中，由调用方自行追加。</p>
     *
     * @param conversation 会话
     * @return 历史消息列表（按时间从老到新）
     */
    public List<HotTurn> composeHistory(AgentConversationInfo conversation) {
        Long conversationId = conversation.getId();
        String key = RedisKeyConstants.chatHotTurns(conversationId);

        List<HotTurn> hotTurns = readHotTurns(key);
        if (!hotTurns.isEmpty()) {
            return hotTurns;
        }

        return warmFromDb(conversationId, key);
    }

    /**
     * 获取格式化后的摘要块，用于注入 system prompt。
     *
     * @param conversation 会话
     * @return 摘要块文本，无摘要时返回 null
     */
    public String getSummaryBlock(AgentConversationInfo conversation) {
        return summaryService.formatSummaryBlock(conversation.getSummary());
    }

    /**
     * 完成一轮对话：推入热缓存；若超出热缓存容量则挤出最老的一轮并异步更新摘要。
     *
     * @param conversationId 会话 ID
     * @param userMessage    本轮用户消息
     * @param assistantReply 本轮助手回复
     * @param modelName      使用的模型（用于摘要）
     */
    public void completeTurn(Long conversationId, String userMessage,
                             String assistantReply, String modelName) {
        log.info("[SummaryDebug] completeTurn 被调用 conversationId={} userLen={} assistantLen={} model={}",
                conversationId,
                userMessage == null ? 0 : userMessage.length(),
                assistantReply == null ? 0 : assistantReply.length(),
                modelName);

        if (conversationId == null
                || StringUtils.isBlank(userMessage)
                || StringUtils.isBlank(assistantReply)) {
            log.info("[SummaryDebug] completeTurn 被拦截，参数为空 conversationId={}", conversationId);
            return;
        }

        String key = RedisKeyConstants.chatHotTurns(conversationId);
        int hotTurns = conversationProperties.getHotTurns();

        try {
            HotTurn turn = new HotTurn();
            turn.setUser(userMessage.trim());
            turn.setAssistant(assistantReply.trim());
            String json = objectMapper.writeValueAsString(turn);

            redisTemplate.opsForList().rightPush(key, json);
            redisTemplate.expire(key,
                    Duration.ofSeconds(conversationProperties.getHotCacheTtlSeconds()));

            Long size = redisTemplate.opsForList().size(key);
            log.info("[SummaryDebug] 热缓存写入后 size={} hotTurns={} conversationId={}",
                    size, hotTurns, conversationId);

            if (size != null && size > hotTurns) {
                String evicted = redisTemplate.opsForList().leftPop(key);
                Long newSize = redisTemplate.opsForList().size(key);
                log.info("[SummaryDebug] 热缓存溢出，触发增量摘要 conversationId={} evictedLen={} newSize={}",
                        conversationId,
                        evicted == null ? 0 : evicted.length(),
                        newSize);
                if (StringUtils.isNotBlank(evicted)) {
                    HotTurn evictedTurn = objectMapper.readValue(evicted, HotTurn.class);
                    summaryAsyncService.asyncUpdateSummary(conversationId, evictedTurn, modelName);
                }
            } else {
                log.info("[SummaryDebug] 热缓存未溢出 size={} hotTurns={} conversationId={}",
                        size, hotTurns, conversationId);
            }
        } catch (Exception e) {
            log.warn("[SummaryDebug] 热缓存写入失败 conversationId={} msg={}",
                    conversationId, e.getMessage(), e);
        }
    }

    /**
     * 清空指定会话的热缓存（例如会话删除时）。
     */
    public void clearCache(Long conversationId) {
        if (conversationId == null) {
            return;
        }
        try {
            redisTemplate.delete(RedisKeyConstants.chatHotTurns(conversationId));
        } catch (Exception e) {
            log.warn("清空热缓存失败 conversationId={}", conversationId, e);
        }
    }

    // ========================= 内部方法 =========================

    private List<HotTurn> readHotTurns(String key) {
        try {
            List<String> raw = redisTemplate.opsForList()
                    .range(key, 0, conversationProperties.getHotTurns() - 1);
            if (raw == null || raw.isEmpty()) {
                return List.of();
            }
            List<HotTurn> turns = new ArrayList<>(raw.size());
            for (String json : raw) {
                try {
                    turns.add(objectMapper.readValue(json, HotTurn.class));
                } catch (Exception e) {
                    log.warn("解析热缓存条目失败 key={}", key, e);
                }
            }
            return turns;
        } catch (Exception e) {
            log.warn("读取热缓存失败 key={}", key, e);
            return List.of();
        }
    }

    /**
     * 从 DB 预热热缓存：取最近 N 轮消息，组装成 turn 对，写回 Redis 并返回。
     */
    private List<HotTurn> warmFromDb(Long conversationId, String key) {
        int hotTurns = conversationProperties.getHotTurns();
        int messageLimit = hotTurns * 2;

        List<AgentMessageInfo> messages = messageService.recentMessages(conversationId, messageLimit);
        if (messages.isEmpty()) {
            return List.of();
        }
        Collections.reverse(messages);

        List<HotTurn> turns = messagesToTurns(messages);
        if (turns.isEmpty()) {
            return List.of();
        }

        try {
            List<String> jsonList = new ArrayList<>();
            for (HotTurn turn : turns) {
                jsonList.add(objectMapper.writeValueAsString(turn));
            }
            redisTemplate.opsForList().rightPushAll(key, jsonList.toArray(new String[0]));
            redisTemplate.expire(key,
                    Duration.ofSeconds(conversationProperties.getHotCacheTtlSeconds()));
            log.info("[SummaryDebug] 从DB预热热缓存 conversationId={} turns={}",
                    conversationId, turns.size());
        } catch (Exception e) {
            log.warn("预热热缓存失败 conversationId={}", conversationId, e);
        }

        return turns;
    }

    /**
     * 将有序消息列表（从老到新）按 user → assistant 配对组装成 turn 列表。
     */
    private List<HotTurn> messagesToTurns(List<AgentMessageInfo> messages) {
        List<HotTurn> turns = new ArrayList<>();
        int i = 0;
        while (i < messages.size()) {
            AgentMessageInfo msg = messages.get(i);
            if ("user".equals(msg.getRole())) {
                HotTurn turn = new HotTurn();
                turn.setUser(msg.getContent());
                if (i + 1 < messages.size() && "assistant".equals(messages.get(i + 1).getRole())) {
                    turn.setAssistant(messages.get(i + 1).getContent());
                    i += 2;
                } else {
                    i++;
                }
                if (StringUtils.isNotBlank(turn.getAssistant())) {
                    turns.add(turn);
                }
            } else {
                i++;
            }
        }
        return turns;
    }
}
