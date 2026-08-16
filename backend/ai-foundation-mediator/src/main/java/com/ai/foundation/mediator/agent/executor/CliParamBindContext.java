package com.ai.foundation.mediator.agent.executor;

import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class CliParamBindContext {

    private String userMessage;

    private String instruction;

    private String cliCommandName;

    private String requestSchema;

    private String responseSchema;

    private Map<String, Object> prefilledParams = new HashMap<>();

    private List<String> priorStepResults;
}
