package com.ai.foundation.facade.dto.conversation;

import lombok.Data;

@Data
public class ConversationPageRequest {

    private Long projectId;
    private String productCode;
    private String title;
    private Integer state;
    private long current = 1;
    private long size = 10;
}
