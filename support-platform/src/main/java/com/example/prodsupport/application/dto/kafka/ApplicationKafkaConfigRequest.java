package com.example.prodsupport.application.dto.kafka;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ApplicationKafkaConfigRequest(
        Boolean enabled,
        @NotEmpty(message = "bootstrapServers list must not be empty")
        List<String> bootstrapServers,
        List<String> consumerGroups,
        List<String> topics
) {
    public ApplicationKafkaConfigRequest {
        enabled = enabled == null ? Boolean.TRUE : enabled;
        bootstrapServers = bootstrapServers == null ? List.of() : List.copyOf(bootstrapServers);
        consumerGroups = consumerGroups == null ? List.of() : List.copyOf(consumerGroups);
        topics = topics == null ? List.of() : List.copyOf(topics);
    }
}
