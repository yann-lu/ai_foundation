package com.ai.foundation.facade.dto.cli;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CliCommandSaveRequest {

    private Long id;

    @NotBlank(message = "命令前缀不能为空")
    @Size(max = 64, message = "命令前缀最长64字符")
    private String commandPrefix;

    @NotBlank(message = "命令分组不能为空")
    @Size(max = 64, message = "命令分组最长64字符")
    private String commandGroup;

    @NotBlank(message = "命令动作不能为空")
    @Size(max = 64, message = "命令动作最长64字符")
    private String commandAction;

    @NotBlank(message = "命令名不能为空")
    @Size(max = 128, message = "命令名最长128字符")
    @Pattern(regexp = "^[a-z0-9_]+$", message = "命令名只能包含小写字母、数字和下划线")
    private String commandName;

    @Size(max = 1024, message = "命令模板最长1024字符")
    private String cliTemplate;

    @NotBlank(message = "功能描述不能为空")
    @Size(max = 2048, message = "功能描述最长2048字符")
    private String description;

    @NotBlank(message = "命令类型不能为空")
    private String commandType;

    private Integer state;

    private List<CliParamDTO> params;

    private ToolDefinitionDTO tool;

    private PageDefinitionDTO page;

    private List<CliRecallTagDTO> recallTags;
}
