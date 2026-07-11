package com.ai.foundation.facade.dto.model;

import lombok.Data;

@Data
public class AgentModelConfigPageRequest {

    private Long projectId;
    private String modelName;
    private String modelType;
    private Integer state;
    private long current = 1;
    private long size = 10;
}
