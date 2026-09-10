package com.example.prodsupport.ai.model;

import java.time.Instant;

public record SupportErrorDto(
        Instant timestamp,
        String level,
        String type,
        String message
) {}
