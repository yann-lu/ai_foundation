package com.ai.foundation.mediator.model;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "agent.retrieval")
public class AgentRetrievalProperties {

    private int topK = 5;
    private double similarityThreshold = 0.6;
}
