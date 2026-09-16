package com.example.prodsupport.application.dto.kafka;

import com.example.prodsupport.domain.kafka.ApplicationKafkaConfig;

import java.time.OffsetDateTime;
import java.util.List;

public record ApplicationKafkaConfigResponse(
        Long id,
        Long applicationId,
        String applicationName,
        String environment,
        boolean enabled,
        List<String> bootstrapServers,
        List<String> consumerGroups,
        List<String> topics,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static ApplicationKafkaConfigResponse fromEntity(ApplicationKafkaConfig config) {
        return new ApplicationKafkaConfigResponse(
                config.getId(),
                config.getApplication().getId(),
                config.getApplication().getApplicationName(),
                config.getApplication().getEnvironment(),
                config.isEnabled(),
                config.getBootstrapServerList(),
                List.copyOf(config.getConsumerGroupNames()),
                List.copyOf(config.getTopicNames()),
                config.getCreatedAt(),
                config.getUpdatedAt()
        );
    }
}
