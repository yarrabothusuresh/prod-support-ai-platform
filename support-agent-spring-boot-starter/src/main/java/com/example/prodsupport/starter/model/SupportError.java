package com.example.prodsupport.starter.model;

import java.time.Instant;

public record SupportError(
        Instant timestamp,
        String level,
        String type,
        String message
) {}
