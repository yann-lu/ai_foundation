package com.ai.foundation.facade.dto.chat;

import com.ai.foundation.com.enums.ChatStreamEventTypeEnum;
import lombok.Data;

import java.io.Serializable;

@Data
public class ChatStreamChunkDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String eventType;
    private String content;
    private Long timestamp;

    public static ChatStreamChunkDTO of(ChatStreamEventTypeEnum eventType, String content) {
        ChatStreamChunkDTO chunk = new ChatStreamChunkDTO();
        chunk.eventType = eventType.getCode();
        chunk.content = content;
        chunk.timestamp = System.currentTimeMillis();
        return chunk;
    }

    public static ChatStreamChunkDTO of(ChatStreamEventTypeEnum eventType) {
        return of(eventType, null);
    }
}
