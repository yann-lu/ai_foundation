package com.ai.foundation.mediator.agent.context;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class AgentExecutionContext {

    private String hotelCode;

    private String accessToken;

    private Long projectId;

    private String projectCode;

    private Long userId;

    private Map<String, Object> extra = new HashMap<>();

    public AgentExecutionContext() {
    }

    public AgentExecutionContext(Long projectId, String hotelCode, String accessToken) {
        this.projectId = projectId;
        this.hotelCode = hotelCode;
        this.accessToken = accessToken;
    }
}
