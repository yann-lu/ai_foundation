package com.ai.foundation.mediator.prompt;

import lombok.Data;

@Data
public class PromptVariableDefinition {

    private String name;

    private String label;

    private String type;

    private Boolean required;

    private String description;

    private Object defaultValue;
}
