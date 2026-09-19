package com.example.prodsupport.tracing.dto;

import java.util.List;

public record TraceDetailApiResponse(
        String traceId,
        long durationMs,
        boolean hasError,
        List<SpanDetailDto> spans,
        List<String> warnings
) {}
