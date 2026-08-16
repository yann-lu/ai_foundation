package com.ai.foundation.facade.dto.run;

import lombok.Data;

@Data
public class RunEventDTO {
    private Long id;
    private Long runId;
    private String eventType;
    private String taskState;
    private String eventData;
    private Long timestamp;
}
