package com.example.prodsupport.logging.model;

import java.time.Instant;
import java.util.List;

public record LogSearchCriteria(
        String applicationName,
        String environment,
        Instant startTime,
        Instant endTime,
        List<String> levels,
        String keyword,
        String correlationId,
        String traceId,
        Integer limit,
        String indexPattern
) {
    public LogSearchCriteria {
        if (levels == null) {
            levels = List.of();
        }
    }

    public LogSearchCriteria(
            String applicationName,
            String environment,
            Instant startTime,
            Instant endTime,
            List<String> levels,
            String keyword,
            String correlationId,
            Integer limit,
            String indexPattern
    ) {
        this(applicationName, environment, startTime, endTime, levels, keyword, correlationId, null, limit, indexPattern);
    }
}
