package com.ai.foundation.mediator.chat;

/**
 * 流式聊天 chunk，将 AI 的思考内容 (reasoning) 与正文 (content) 分离。
 *
 * <p>对于支持 reasoning_content 的模型（如 Moonshot Kimi K2 thinking 模式），
 * 每个 chunk 可能只包含 reasoning、只包含 content，或两者都有。
 * 对于不支持的模型，reasoning 始终为空。
 *
 * @param reasoning AI 思考内容片段（可能为 null 或空）
 * @param content   AI 回复正文片段（可能为 null 或空）
 */
public record ChatStreamChunk(String reasoning, String content) {

    public boolean hasReasoning() {
        return reasoning != null && !reasoning.isEmpty();
    }

    public boolean hasContent() {
        return content != null && !content.isEmpty();
    }
}
