package com.example.prodsupport.infrastructure.kafka.model;

import java.util.List;

public record KafkaTopicData(
        String topic,
        int partitionCount,
        List<TopicPartitionMetadataData> partitions,
        boolean internal,
        String warning
) {
    public KafkaTopicData {
        partitions = partitions == null ? List.of() : List.copyOf(partitions);
    }

    public static KafkaTopicData notFound(String topic, String warning) {
        return new KafkaTopicData(topic, 0, List.of(), false, warning);
    }
}
