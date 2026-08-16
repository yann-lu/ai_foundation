package com.ai.foundation.facade.dto.cli;

import lombok.Data;

@Data
public class PageDefinitionDTO {

    private Long id;
    private String pageName;
    private String description;
    private String pagePrefix;
    private String pageRoute;
    private String displayType;
    private String targetType;
    private String resourceProject;
    private String resourceIds;
}
