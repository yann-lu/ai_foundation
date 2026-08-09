package com.ai.foundation.mediator.chat;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "agent.conversation")
public class AgentConversationProperties {

    /**
     * 热缓存保留的最近对话轮数（一轮 = 用户消息 + 助手回复）。
     * 超过该数量时，最老的一轮会被挤出热缓存并触发增量摘要。
     */
    private int hotTurns = 5;

    /**
     * 摘要最大字符数。
     */
    private int maxSummaryChars = 800;

    /**
     * 热缓存 Redis TTL（秒）。默认 7 天。
     */
    private long hotCacheTtlSeconds = 7 * 24 * 60 * 60L;

    /**
     * 摘要使用的模型名称。为空时跟随会话当前模型。
     * 建议配置成本较低的小模型（如 deepseek-v4-flash），
     * 摘要对模型推理能力要求不高，主要做信息压缩。
     */
    private String summaryModel;
}
