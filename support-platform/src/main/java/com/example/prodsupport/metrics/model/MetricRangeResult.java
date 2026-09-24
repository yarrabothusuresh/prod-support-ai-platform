package com.example.prodsupport.metrics.model;

import java.util.List;

public record MetricRangeResult(
        String query,
        List<MetricSeriesDto> series,
        boolean hasData,
        List<String> warnings
) {
    public List<MetricSampleDto> getPrimarySeriesSamples() {
        if (!hasData || series == null || series.isEmpty()) {
            return List.of();
        }
        return series.get(0).samples();
    }
}
