package com.ai.foundation.biz.converter;

import com.ai.foundation.dal.entity.AgentApiSchemaConfig;
import com.ai.foundation.facade.dto.schema.ApiSchemaConfigDTO;
import com.ai.foundation.facade.dto.schema.ApiSchemaConfigSaveRequest;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface ApiSchemaConfigConverter {

    ApiSchemaConfigDTO toDto(AgentApiSchemaConfig entity);

    AgentApiSchemaConfig toEntity(ApiSchemaConfigSaveRequest request);

    void updateEntity(ApiSchemaConfigSaveRequest request, @MappingTarget AgentApiSchemaConfig entity);
}
