package com.ai.foundation.facade.dto.conversation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class ConversationCreateRequest {

    @NotBlank(message = "产品编码不能为空")
    @Size(max = 64, message = "产品编码最长64字符")
    private String productCode;

    @Size(max = 64, message = "集团编码最长64字符")
    private String blocCode;

    @Size(max = 64, message = "酒店编码最长64字符")
    private String hotelCode;

    private Map<String, Object> contextVariables;

    private Long userId;

    @Size(max = 256, message = "标题最长256字符")
    private String title;

    private String modelName;
}
