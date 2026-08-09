package com.ai.foundation.facade.dto.run;

import lombok.Data;

@Data
public class RequestMessageDTO {
    private String role;
    private String content;
}
