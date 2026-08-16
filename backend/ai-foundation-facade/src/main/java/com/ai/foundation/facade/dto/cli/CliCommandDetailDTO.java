package com.ai.foundation.facade.dto.cli;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class CliCommandDetailDTO {

    private Long id;
    private String commandName;
    private String commandPrefix;
    private String commandGroup;
    private String commandAction;
    private String cliTemplate;
    private String description;
    private String commandType;
    private Integer state;
    private List<CliParamDTO> params;
    private ToolDefinitionDTO tool;
    private PageDefinitionDTO page;
    private List<CliRecallTagDTO> recallTags;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
