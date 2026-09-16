package com.example.prodsupport.infrastructure.kafka.model;

import java.util.List;

public record KafkaClusterDiagnosticData(
        boolean reachable,
        String clusterId,
        int brokerCount,
        List<String> nodes,
        String warning
) {
    public KafkaClusterDiagnosticData {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
    }

    public static KafkaClusterDiagnosticData unreachable(String warning) {
        return new KafkaClusterDiagnosticData(false, null, 0, List.of(), warning);
    }
}
