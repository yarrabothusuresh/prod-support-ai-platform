package com.example.prodsupport.infrastructure.kafka.model;

import java.util.List;

public record TopicPartitionMetadataData(
        int partition,
        int leader,
        List<Integer> replicas,
        List<Integer> isr
) {
    public TopicPartitionMetadataData {
        replicas = replicas == null ? List.of() : List.copyOf(replicas);
        isr = isr == null ? List.of() : List.copyOf(isr);
    }
}
