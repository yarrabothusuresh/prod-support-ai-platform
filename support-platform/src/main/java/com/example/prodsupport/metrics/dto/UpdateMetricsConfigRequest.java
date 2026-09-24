package com.example.prodsupport.metrics.dto;

public record UpdateMetricsConfigRequest(
        Boolean enabled,
        String prometheusJob,
        String applicationLabel
) {}
