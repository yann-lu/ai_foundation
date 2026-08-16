package com.ai.foundation.mediator.mcp;

import lombok.Data;

@Data
public class McpToolCallResult {

    private boolean success;

    private String content;

    private String errorMessage;

    public static McpToolCallResult success(String content) {
        McpToolCallResult result = new McpToolCallResult();
        result.setSuccess(true);
        result.setContent(content);
        return result;
    }

    public static McpToolCallResult failure(String errorMessage) {
        McpToolCallResult result = new McpToolCallResult();
        result.setSuccess(false);
        result.setErrorMessage(errorMessage);
        return result;
    }
}
