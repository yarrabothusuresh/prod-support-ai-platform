package com.example.prodsupport.logging.model;

public record LoggingStatusResponse(
        String provider,
        boolean available,
        String indexPattern,
        String lastCheckedAt,
        String message
) {}
