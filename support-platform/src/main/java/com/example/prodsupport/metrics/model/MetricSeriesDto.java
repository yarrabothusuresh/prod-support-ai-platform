package com.example.prodsupport.metrics.model;

import java.util.List;
import java.util.Map;

public record MetricSeriesDto(
        Map<String, String> labels,
        List<MetricSampleDto> samples
) {
    public Double getLatestValue() {
        if (samples == null || samples.isEmpty()) {
            return null;
        }
        return samples.get(samples.size() - 1).value();
    }
}
