package com.ai.foundation.facade.dto.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AgentProjectSaveRequest {

    private Long id;

    @NotBlank(message = "项目名称不能为空")
    @Size(max = 128, message = "项目名称最长128字符")
    private String projectName;

    @NotBlank(message = "项目编码不能为空")
    @Size(max = 64, message = "项目编码最长64字符")
    private String projectCode;

    @Size(max = 1024, message = "描述最长1024字符")
    private String description;

    private Integer state;
}
