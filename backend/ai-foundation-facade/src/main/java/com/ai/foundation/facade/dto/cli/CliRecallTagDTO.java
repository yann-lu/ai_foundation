package com.ai.foundation.facade.dto.cli;

import lombok.Data;

@Data
public class CliRecallTagDTO {

    private Long id;
    private String tagType;
    private String tagValue;
    private Integer weight;
    private String matchMode;
    private Integer sortOrder;
}
