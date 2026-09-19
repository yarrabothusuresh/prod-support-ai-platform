package com.example.prodsupport.tracing.dto;

import java.util.List;

public record TraceSearchApiResponse(
        String applicationName,
        String environment,
        List<TraceSummaryDto> traces,
        List<String> warnings
) {}
