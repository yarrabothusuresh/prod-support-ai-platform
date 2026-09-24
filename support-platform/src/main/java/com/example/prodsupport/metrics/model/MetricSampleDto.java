package com.example.prodsupport.metrics.model;

import java.time.Instant;

public record MetricSampleDto(
        Instant timestamp,
        double value
) {}
