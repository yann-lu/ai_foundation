package com.ai.foundation.facade.dto.run;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CreateRunResponse {

    private String runCode;
    private String traceId;
}
