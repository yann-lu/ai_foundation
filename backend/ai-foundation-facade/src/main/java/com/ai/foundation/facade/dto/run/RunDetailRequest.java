package com.ai.foundation.facade.dto.run;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RunDetailRequest {

    @NotBlank(message = "Run编码不能为空")
    private String runCode;
}
