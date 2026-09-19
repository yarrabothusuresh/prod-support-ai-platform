package com.example.prodsupport.tracing.dto;

import java.util.Map;

public record SpanDetailDto(
        String spanId,
        String parentSpanId,
        String service,
        String operation,
        long durationMs,
        long startOffsetMs,
        String status,
        String errorCategory,
        String sanitizedErrorDescription,
        Map<String, String> tags
) {}
