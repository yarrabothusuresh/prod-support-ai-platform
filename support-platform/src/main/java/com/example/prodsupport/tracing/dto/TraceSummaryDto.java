package com.example.prodsupport.tracing.dto;

public record TraceSummaryDto(
        String traceId,
        String rootService,
        String operation,
        long durationMs,
        boolean hasError,
        int spanCount,
        String startedAt
) {}
