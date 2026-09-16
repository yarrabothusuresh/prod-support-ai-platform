package com.example.prodsupport.infrastructure.kafka.model;

import java.util.List;

public record ConsumerGroupMemberData(
        String memberId,
        String clientId,
        String host,
        List<String> assignedPartitions
) {
    public ConsumerGroupMemberData {
        assignedPartitions = assignedPartitions == null ? List.of() : List.copyOf(assignedPartitions);
    }
}
