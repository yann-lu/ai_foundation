package com.ai.foundation.biz.converter;

import com.ai.foundation.dal.entity.AgentMcpServer;
import com.ai.foundation.facade.dto.mcp.AgentMcpServerDTO;
import com.ai.foundation.facade.dto.mcp.AgentMcpServerSaveRequest;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface McpServerConverter {

    AgentMcpServerDTO toDto(AgentMcpServer entity);

    AgentMcpServer toEntity(AgentMcpServerSaveRequest request);

    void updateEntity(AgentMcpServerSaveRequest request, @MappingTarget AgentMcpServer entity);
}
