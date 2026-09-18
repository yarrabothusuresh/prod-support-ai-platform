package com.example.prodsupport.logging.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LogEntryDto(
        String timestamp,
        String level,
        String errorType,
        String message,
        String correlationId,
        String component,
        String logger,
        String traceId
) {}
