package com.ai.foundation.facade.dto.schema;

import lombok.Data;

@Data
public class ApiSchemaConfigPageRequest {

    private String keyword;
    private Integer state;
    private long current = 1;
    private long size = 20;
}
