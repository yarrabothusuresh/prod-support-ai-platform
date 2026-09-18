package com.example.prodsupport.logging.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LogSearchResult(
        String applicationName,
        String environment,
        long totalHits,
        boolean truncated,
        List<LogEntryDto> logs,
        List<String> warnings
) {
    public LogSearchResult {
        if (logs == null) {
            logs = List.of();
        }
        if (warnings == null) {
            warnings = List.of();
        }
    }
}
