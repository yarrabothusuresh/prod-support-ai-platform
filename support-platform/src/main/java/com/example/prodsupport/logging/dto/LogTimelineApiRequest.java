package com.example.prodsupport.logging.dto;

import java.time.Instant;

public record LogTimelineApiRequest(
        Instant startTime,
        Instant endTime,
        String correlationId,
        Integer limit
) {}
