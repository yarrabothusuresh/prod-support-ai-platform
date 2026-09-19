package com.example.prodsupport.tracing.model;

import com.example.prodsupport.tracing.dto.SpanDetailDto;

import java.util.List;
import java.util.Set;

public record TraceDetailResult(
        String traceId,
        long durationMs,
        boolean hasError,
        List<SpanDetailDto> spans,
        Set<String> participatingServices,
        List<String> warnings
) {
    public TraceDetailResult {
        if (spans == null) {
            spans = List.of();
        }
        if (participatingServices == null) {
            participatingServices = Set.of();
        }
        if (warnings == null) {
            warnings = List.of();
        }
    }
}
