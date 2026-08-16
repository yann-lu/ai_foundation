package com.ai.foundation.facade.dto.cli;

import lombok.Data;

@Data
public class BindCapabilityOptionDTO {

    private Long id;
    private String commandName;
    private String commandType;
    private String description;
    private Boolean bound;
}
