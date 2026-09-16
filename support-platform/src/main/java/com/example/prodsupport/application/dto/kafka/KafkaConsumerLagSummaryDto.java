package com.example.prodsupport.application.dto.kafka;

import java.util.List;

public record KafkaConsumerLagSummaryDto(
        String consumerGroup,
        String state,
        int memberCount,
        Long totalLag,
        Long highestPartitionLag,
        String status
) {
}
