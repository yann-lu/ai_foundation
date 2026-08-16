package com.ai.foundation.facade.dto.mcp;

import lombok.Data;

@Data
public class AgentMcpServerDTO {

    private Long id;

    private String serverCode;

    private String serverName;

    private String description;

    private String transportType;

    private String command;

    private String workingDir;

    private String envVars;

    private String baseUrl;

    private String authType;

    private String authConfig;

    private Integer state;

    private String createUser;

    private String modifyUser;

    private String createTime;

    private String updateTime;
}
