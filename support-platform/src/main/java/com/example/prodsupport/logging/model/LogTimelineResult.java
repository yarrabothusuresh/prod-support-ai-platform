package com.example.prodsupport.logging.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LogTimelineResult(
        String applicationName,
        String environment,
        List<LogTimelineEventDto> events,
        List<String> warnings
) {
    public LogTimelineResult {
        if (events == null) {
            events = List.of();
        }
        if (warnings == null) {
            warnings = List.of();
        }
    }
}
