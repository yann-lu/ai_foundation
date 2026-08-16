package com.ai.foundation.facade.dto.schema;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ApiSchemaConfigDTO {

    private Long id;
    private String schemaCode;
    private String schemaName;
    private String baseUrl;
    private String commandPrefix;
    private Integer state;
    private String createUser;
    private String modifyUser;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
