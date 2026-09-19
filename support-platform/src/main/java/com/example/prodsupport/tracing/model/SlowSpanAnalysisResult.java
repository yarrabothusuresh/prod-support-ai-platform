package com.example.prodsupport.tracing.model;

import com.example.prodsupport.tracing.dto.SpanDetailDto;

import java.util.List;

public record SlowSpanAnalysisResult(
        String traceId,
        long totalDurationMs,
        SpanDetailDto longestSpan,
        List<SpanDetailDto> slowSpans,
        List<String> warnings
) {
    public SlowSpanAnalysisResult {
        if (slowSpans == null) {
            slowSpans = List.of();
        }
        if (warnings == null) {
            warnings = List.of();
        }
    }
}
