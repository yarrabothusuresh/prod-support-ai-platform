package com.example.prodsupport.logging.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LogTimelineEventDto(
        String timestamp,
        String level,
        String component,
        String errorType,
        String message,
        String correlationId
) {}
