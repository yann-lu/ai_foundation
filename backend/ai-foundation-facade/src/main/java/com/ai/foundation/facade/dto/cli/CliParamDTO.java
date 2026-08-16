package com.ai.foundation.facade.dto.cli;

import lombok.Data;

@Data
public class CliParamDTO {

    private Long id;
    private String paramName;
    private String paramFlag;
    private String paramType;
    private String itemType;
    private Integer isRequired;
    private String description;
    private String defaultValue;
    private Integer sortOrder;
    private String parentParamName;
}
