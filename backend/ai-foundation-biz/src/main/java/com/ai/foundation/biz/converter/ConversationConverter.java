package com.ai.foundation.biz.converter;

import com.ai.foundation.dal.entity.AgentConversationInfo;
import com.ai.foundation.facade.dto.conversation.ConversationDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ConversationConverter {

    ConversationDTO toDto(AgentConversationInfo entity);
}
