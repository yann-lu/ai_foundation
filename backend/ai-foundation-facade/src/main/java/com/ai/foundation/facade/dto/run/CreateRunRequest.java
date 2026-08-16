package com.ai.foundation.facade.dto.run;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateRunRequest {

    @NotBlank(message = "会话编码不能为空")
    private String conversationCode;

    @NotBlank(message = "用户消息不能为空")
    @Size(max = 8192, message = "消息最长8192字符")
    private String userMessage;

    private String systemPrompt;

}
