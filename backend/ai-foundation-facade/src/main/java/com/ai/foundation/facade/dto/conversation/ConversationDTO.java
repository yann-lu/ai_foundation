package com.ai.foundation.facade.dto.conversation;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationDTO {

    private Long id;
    private Long projectId;
    private String productCode;
    private String blocCode;
    private String hotelCode;
    private String conversationCode;
    private Long userId;
    private String title;
    private String summary;
    private String modelProvider;
    private String modelName;
    private Integer isPin;
    private LocalDateTime lastMessageTime;
    private Integer state;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
