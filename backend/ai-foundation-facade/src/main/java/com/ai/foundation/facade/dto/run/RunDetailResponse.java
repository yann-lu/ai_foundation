package com.ai.foundation.facade.dto.run;

import lombok.Data;

import java.util.List;

@Data
public class RunDetailResponse {

    private String runCode;
    private String traceId;
    private String conversationCode;
    private String productCode;
    private String runType;
    private String taskState;

    /** 本次 Run 实际发给模型的消息栈。 */
    private List<RequestMessageDTO> requestMessages;

    /** 模型最终回复正文。 */
    private String reply;

    /** 思考链内容。 */
    private String reasoning;
}
