package com.example.prodsupport.tracing.dto;

public record TracingStatusResponse(
        String provider,
        boolean available,
        String lastCheckedAt,
        String endpoint
) {}
