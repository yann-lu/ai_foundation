package com.ai.foundation.mediator.agent.react.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class ReactCliToolInput {

    private Map<String, Object> params = new HashMap<>();
}
