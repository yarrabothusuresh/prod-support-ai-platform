package com.example.prodsupport.metrics.model;

import java.time.Instant;
import java.util.Map;

public record PrometheusAlertDto(
        String name,
        String state,
        String severity,
        String summary,
        String description,
        String application,
        String environment,
        Instant activeAt,
        Map<String, String> labels
) {
    public String alertName() {
        return name();
    }
}
