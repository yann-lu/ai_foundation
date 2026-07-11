package com.ai.foundation.facade.dto.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AgentModelConfigDTO {

    private Long id;
    private Long projectId;
    private String modelName;
    private String modelType;
    private Integer state;
    private String createUser;
    private String modifyUser;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
