package com.ai.foundation.biz.converter;

import com.ai.foundation.dal.entity.AgentSkillDefinition;
import com.ai.foundation.facade.dto.skill.AgentSkillDTO;
import com.ai.foundation.facade.dto.skill.AgentSkillSaveRequest;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface SkillConverter {

    AgentSkillDTO toDto(AgentSkillDefinition entity);

    AgentSkillDefinition toEntity(AgentSkillSaveRequest request);

    void updateEntity(AgentSkillSaveRequest request, @MappingTarget AgentSkillDefinition entity);
}
