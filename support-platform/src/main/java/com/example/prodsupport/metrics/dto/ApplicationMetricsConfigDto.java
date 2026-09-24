package com.example.prodsupport.metrics.dto;

public record ApplicationMetricsConfigDto(
        Long id,
        Long applicationId,
        String applicationName,
        String environment,
        boolean metricsEnabled,
        String prometheusJob,
        String applicationLabel
) {}
