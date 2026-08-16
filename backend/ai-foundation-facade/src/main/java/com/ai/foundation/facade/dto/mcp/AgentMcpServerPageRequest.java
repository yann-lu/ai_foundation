package com.ai.foundation.facade.dto.mcp;

import lombok.Data;

@Data
public class AgentMcpServerPageRequest {

    private Integer current = 1;

    private Integer size = 10;

    private String keyword;

    private String transportType;

    private Integer state;
}
