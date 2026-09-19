package com.example.prodsupport.tracing.dto;

public record TraceTimelineEventDto(
        String timestamp,
        long offsetMs,
        String service,
        String operation,
        String eventDescription,
        String status
) {}
