package com.ai.foundation.facade.dto.cli;

import lombok.Data;

@Data
public class ToolDefinitionDTO {

    private Long id;
    private String toolName;
    private String description;
    private String schemaCode;
    private String url;
    private String method;
    private String authType;
    private String requestSchema;
    private String responseSchema;
}
