package com.ai.foundation.facade.dto.schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ApiSchemaConfigSaveRequest {

    private Long id;

    @NotBlank(message = "Schema编码不能为空")
    @Size(max = 64, message = "Schema编码最长64字符")
    private String schemaCode;

    @NotBlank(message = "Schema名称不能为空")
    @Size(max = 128, message = "Schema名称最长128字符")
    private String schemaName;

    @NotBlank(message = "Base URL不能为空")
    @Size(max = 512, message = "Base URL最长512字符")
    private String baseUrl;

    @Size(max = 64, message = "CLI命令前缀最长64字符")
    private String commandPrefix;

    private Integer state;
}
