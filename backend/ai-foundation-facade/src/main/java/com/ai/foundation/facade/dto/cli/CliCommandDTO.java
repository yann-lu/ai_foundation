package com.ai.foundation.facade.dto.cli;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CliCommandDTO {

    private Long id;
    private String commandName;
    private String commandPrefix;
    private String commandGroup;
    private String commandAction;
    private String cliTemplate;
    private String description;
    private String commandType;
    private Integer state;
    private Integer boundCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
