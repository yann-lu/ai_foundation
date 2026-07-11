package com.ai.foundation.com.stream;

import lombok.Data;

import java.io.Serializable;

@Data
public class RunStreamEnvelope implements Serializable {

    private static final long serialVersionUID = 1L;

    private String eventType;
    private String runCode;
    private String conversationCode;
    private Long timestamp;
    private String taskState;
    private Object data;
    private String traceId;
}
