package com.ai.foundation.biz.converter;

import com.ai.foundation.dal.entity.AgentCliCommand;
import com.ai.foundation.dal.entity.AgentCliParam;
import com.ai.foundation.dal.entity.AgentCliRecallTag;
import com.ai.foundation.dal.entity.AgentPageDefinition;
import com.ai.foundation.dal.entity.AgentToolDefinition;
import com.ai.foundation.facade.dto.cli.CliCommandDTO;
import com.ai.foundation.facade.dto.cli.CliCommandDetailDTO;
import com.ai.foundation.facade.dto.cli.CliCommandSaveRequest;
import com.ai.foundation.facade.dto.cli.CliParamDTO;
import com.ai.foundation.facade.dto.cli.CliRecallTagDTO;
import com.ai.foundation.facade.dto.cli.PageDefinitionDTO;
import com.ai.foundation.facade.dto.cli.ToolDefinitionDTO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CliConverter {

    CliCommandDTO toDto(AgentCliCommand entity);

    CliCommandDetailDTO toDetailDto(AgentCliCommand entity);

    AgentCliCommand toEntity(CliCommandSaveRequest request);

    void updateEntity(CliCommandSaveRequest request, @MappingTarget AgentCliCommand entity);

    List<CliParamDTO> toParamDtoList(List<AgentCliParam> params);

    List<AgentCliParam> toParamEntityList(List<CliParamDTO> params);

    ToolDefinitionDTO toToolDto(AgentToolDefinition entity);

    AgentToolDefinition toToolEntity(ToolDefinitionDTO dto);

    PageDefinitionDTO toPageDto(AgentPageDefinition entity);

    AgentPageDefinition toPageEntity(PageDefinitionDTO dto);

    List<CliRecallTagDTO> toRecallTagDtoList(List<AgentCliRecallTag> tags);

    List<AgentCliRecallTag> toRecallTagEntityList(List<CliRecallTagDTO> tags);
}
