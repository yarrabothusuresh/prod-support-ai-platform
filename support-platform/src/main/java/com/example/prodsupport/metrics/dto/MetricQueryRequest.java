package com.example.prodsupport.metrics.dto;

import jakarta.validation.constraints.NotNull;

public record MetricQueryRequest(
        @NotNull(message = "metricType is required")
        MetricQueryType metricType,
        Integer minutes
) {}
