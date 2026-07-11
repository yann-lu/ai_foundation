package com.ai.foundation.facade.dto.project;

import lombok.Data;

@Data
public class AgentProjectPageRequest {

    private String projectName;
    private String projectCode;
    private Integer state;
    private long current = 1;
    private long size = 10;
}
