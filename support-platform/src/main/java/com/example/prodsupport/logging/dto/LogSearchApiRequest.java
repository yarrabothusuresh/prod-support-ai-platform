package com.example.prodsupport.logging.dto;

import java.time.Instant;
import java.util.List;

public record LogSearchApiRequest(
        Instant startTime,
        Instant endTime,
        List<String> levels,
        String keyword,
        Integer limit
) {}
