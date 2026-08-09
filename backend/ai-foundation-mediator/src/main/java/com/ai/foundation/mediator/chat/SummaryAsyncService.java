package com.ai.foundation.mediator.chat;

import com.ai.foundation.mediator.config.SummaryAsyncConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 异步摘要服务：独立 Bean，确保 {@code @Async} 通过 Spring AOP 代理生效。
 *
 * <p>注意：{@code @Async} 不能在类内部自调用，必须通过代理对象调用，
 * 所以把异步方法单独抽到这个类里，由 {@link ChatHistoryComposer} 注入后调用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SummaryAsyncService {

    private final ConversationSummaryService summaryService;

    /**
     * 异步执行增量摘要更新。
     * 运行在 {@link SummaryAsyncConfig#SUMMARY_EXECUTOR} 线程池。
     */
    @Async(SummaryAsyncConfig.SUMMARY_EXECUTOR)
    public void asyncUpdateSummary(Long conversationId, ChatHistoryComposer.HotTurn evictedTurn, String modelName) {
        log.info("[SummaryDebug] 异步摘要开始 conversationId={} thread={} userLen={} assistantLen={}",
                conversationId, Thread.currentThread().getName(),
                evictedTurn.getUser() == null ? 0 : evictedTurn.getUser().length(),
                evictedTurn.getAssistant() == null ? 0 : evictedTurn.getAssistant().length());
        try {
            summaryService.updateSummary(conversationId,
                    evictedTurn.getUser(), evictedTurn.getAssistant(), modelName);
        } catch (Exception e) {
            log.warn("异步摘要更新失败 conversationId={}", conversationId, e);
        }
    }
}
