package com.ai.foundation.biz.converter;

import com.ai.foundation.dal.entity.AgentModelConfig;
import com.ai.foundation.facade.dto.model.AgentModelConfigDTO;
import com.ai.foundation.facade.dto.model.AgentModelConfigSaveRequest;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface ModelConfigConverter {

    AgentModelConfigDTO toDto(AgentModelConfig entity);

    AgentModelConfig toEntity(AgentModelConfigSaveRequest request);

    void updateEntity(AgentModelConfigSaveRequest request, @MappingTarget AgentModelConfig entity);
}
