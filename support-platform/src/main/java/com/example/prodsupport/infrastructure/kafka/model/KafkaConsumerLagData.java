package com.example.prodsupport.infrastructure.kafka.model;

import java.util.List;

public record KafkaConsumerLagData(
        String consumerGroup,
        String state,
        int memberCount,
        Long totalLag,
        Long highestPartitionLag,
        String lagStatus,
        List<PartitionLagData> partitions,
        List<String> warnings
) {
    public static final String LAG_NORMAL = "NORMAL";
    public static final String LAG_WARNING = "WARNING";
    public static final String LAG_CRITICAL = "CRITICAL";
    public static final String LAG_UNKNOWN = "UNKNOWN";

    public KafkaConsumerLagData {
        partitions = partitions == null ? List.of() : List.copyOf(partitions);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
