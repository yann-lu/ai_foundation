package com.ai.foundation.facade.dto.run;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RunItemDTO {
    private Long id;
    private String runCode;
    private String runType;
    private String taskState;
    private Integer tokensPrompt;
    private Integer tokensCompletion;
    private BigDecimal cost;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
