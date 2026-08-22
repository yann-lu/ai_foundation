package com.ai.foundation.facade.dto.run;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RunTaskDTO {

    private Long id;
    private String taskCode;
    private String taskType;
    private String capabilityType;
    private Long refId;
    private String refName;
    private String taskState;
    private String errorMessage;
    private Long costMs;
    private LocalDateTime createTime;
    /** 工具调用入参（JSON 字符串） */
    private String inputParams;
    /** 工具调用结果（JSON / 文本） */
    private String resultRef;
}
