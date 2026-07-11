package com.ai.foundation.facade.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChatSyncResponse {

    private String content;
    private int tokenCount;
    private long durationMs;
    private Long assistantMessageId;
}
