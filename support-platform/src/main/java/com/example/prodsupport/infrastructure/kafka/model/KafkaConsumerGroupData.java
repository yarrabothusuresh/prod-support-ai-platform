package com.example.prodsupport.infrastructure.kafka.model;

import java.util.List;

public record KafkaConsumerGroupData(
        String consumerGroup,
        String state,
        int memberCount,
        String coordinator,
        List<ConsumerGroupMemberData> members,
        String warning
) {
    public KafkaConsumerGroupData {
        members = members == null ? List.of() : List.copyOf(members);
    }

    public static KafkaConsumerGroupData notFound(String consumerGroup, String warning) {
        return new KafkaConsumerGroupData(consumerGroup, "NOT_FOUND", 0, null, List.of(), warning);
    }
}
