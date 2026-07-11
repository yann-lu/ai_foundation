package com.ai.foundation.facade.dto.conversation;

import lombok.Data;

import java.util.List;

@Data
public class ConversationDetailDTO {

    private ConversationDTO conversation;
    private List<MessageDTO> messages;
}
