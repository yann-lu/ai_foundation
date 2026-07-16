package com.ai.foundation.facade.dto.project;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AgentProjectDTO {

    private Long id;
    private String projectName;
    private String projectCode;
    private String description;
    private String systemPrompt;
    private String promptVariables;
    private Integer state;
    private String createUser;
    private String modifyUser;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
