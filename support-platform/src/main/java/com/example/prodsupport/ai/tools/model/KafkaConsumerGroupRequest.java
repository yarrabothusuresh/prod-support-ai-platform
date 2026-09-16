package com.example.prodsupport.ai.tools.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record KafkaConsumerGroupRequest(
        @JsonProperty(required = true)
        @JsonPropertyDescription("The registered application name (e.g. payment-service)")
        String applicationName,

        @JsonProperty(required = true)
        @JsonPropertyDescription("The environment to inspect (e.g. local, dev, prod)")
        String environment,

        @JsonProperty(required = true)
        @JsonPropertyDescription("The Kafka consumer group name configured for the application (e.g. payment-processing-group)")
        String consumerGroup
) {
}
