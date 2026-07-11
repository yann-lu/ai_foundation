package com.ai.foundation.facade.dto.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AgentModelConfigSaveRequest {

    private Long id;

    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    @NotBlank(message = "模型名称不能为空")
    @Size(max = 128, message = "模型名称最长128字符")
    private String modelName;

    @NotBlank(message = "模型类型不能为空")
    private String modelType;

    private Integer state;
}
