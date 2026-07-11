package com.ai.foundation.facade.dto.run;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RunCancelRequest {

    @NotBlank(message = "Run编码不能为空")
    private String runCode;

    private String operator;
}
