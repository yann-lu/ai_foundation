package com.ai.foundation.biz.converter;

import com.ai.foundation.dal.entity.AgentMessageInfo;
import com.ai.foundation.facade.dto.conversation.MessageDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface MessageConverter {

    MessageDTO toDto(AgentMessageInfo entity);
}
