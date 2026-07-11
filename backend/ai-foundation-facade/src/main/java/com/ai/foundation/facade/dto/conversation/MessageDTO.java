package com.ai.foundation.facade.dto.conversation;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MessageDTO {

    private Long id;
    private Long conversationId;
    private String role;
    private String content;
    private Integer tokenCount;
    private Integer durationMs;
    private String attachments;
    private String clientIp;
    private Integer state;
    private LocalDateTime createTime;
}
