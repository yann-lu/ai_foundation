package com.ai.foundation.mediator.model;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "agent.ai.models")
public class AiModelsProperties {

    private String chat = "deepseek-v4-pro";
    private String embedding = "bge-m3";
}
