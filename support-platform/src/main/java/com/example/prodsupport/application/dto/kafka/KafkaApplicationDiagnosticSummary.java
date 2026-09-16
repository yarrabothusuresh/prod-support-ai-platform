package com.example.prodsupport.application.dto.kafka;

import java.util.List;

public record KafkaApplicationDiagnosticSummary(
        boolean enabled,
        boolean clusterReachable,
        String clusterId,
        int brokerCount,
        List<KafkaConsumerLagSummaryDto> consumerGroups,
        List<String> topics,
        List<String> warnings
) {
    public KafkaApplicationDiagnosticSummary {
        consumerGroups = consumerGroups == null ? List.of() : List.copyOf(consumerGroups);
        topics = topics == null ? List.of() : List.copyOf(topics);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public static KafkaApplicationDiagnosticSummary disabled(String warning) {
        return new KafkaApplicationDiagnosticSummary(false, false, null, 0, List.of(), List.of(), List.of(warning));
    }
}
