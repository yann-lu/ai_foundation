package com.ai.foundation.facade.dto.run;

import lombok.Data;

@Data
public class RunDetailResponse {

    private String runCode;
    private String traceId;
    private String conversationCode;
    private String productCode;
    private String runType;
    private String taskState;
}
