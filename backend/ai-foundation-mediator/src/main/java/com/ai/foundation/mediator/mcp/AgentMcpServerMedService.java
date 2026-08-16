package com.ai.foundation.mediator.mcp;

import com.ai.foundation.com.response.PageResult;
import com.ai.foundation.facade.dto.mcp.AgentMcpServerDTO;
import com.ai.foundation.facade.dto.mcp.AgentMcpServerPageRequest;
import com.ai.foundation.facade.dto.mcp.AgentMcpServerSaveRequest;

public interface AgentMcpServerMedService {

    PageResult<AgentMcpServerDTO> page(AgentMcpServerPageRequest request);

    AgentMcpServerDTO detail(Long id);

    void save(AgentMcpServerSaveRequest request, String operator);

    void delete(Long id, String operator);
}
