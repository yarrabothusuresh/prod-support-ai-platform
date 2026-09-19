package com.example.prodsupport.tracing.model;

import com.example.prodsupport.tracing.dto.TraceSummaryDto;

import java.util.List;

public record TraceSearchResult(
        String applicationName,
        String environment,
        List<TraceSummaryDto> traces,
        List<String> warnings
) {
    public TraceSearchResult {
        if (traces == null) {
            traces = List.of();
        }
        if (warnings == null) {
            warnings = List.of();
        }
    }
}
