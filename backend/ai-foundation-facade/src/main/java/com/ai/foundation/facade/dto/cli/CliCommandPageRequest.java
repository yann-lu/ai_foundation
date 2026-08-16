package com.ai.foundation.facade.dto.cli;

import lombok.Data;

@Data
public class CliCommandPageRequest {

    private String keyword;
    private String commandType;
    private String commandPrefix;
    private Integer state;
    private long current = 1;
    private long size = 20;
}
