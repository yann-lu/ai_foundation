package com.ai.foundation.biz.converter;

import com.ai.foundation.dal.entity.AgentProject;
import com.ai.foundation.facade.dto.project.AgentProjectDTO;
import com.ai.foundation.facade.dto.project.AgentProjectSaveRequest;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface ProjectConverter {

    AgentProjectDTO toDto(AgentProject entity);

    AgentProject toEntity(AgentProjectSaveRequest request);

    void updateEntity(AgentProjectSaveRequest request, @MappingTarget AgentProject entity);
}
