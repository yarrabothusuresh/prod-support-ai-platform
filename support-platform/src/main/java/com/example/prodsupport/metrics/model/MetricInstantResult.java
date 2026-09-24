package com.example.prodsupport.metrics.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record MetricInstantResult(
        String query,
        List<MetricSeriesDto> series,
        boolean hasData,
        List<String> warnings
) {
    public MetricInstantResult(Double singleValue, boolean hasData, List<String> warnings) {
        this(
                "",
                hasData && singleValue != null
                        ? List.of(new MetricSeriesDto(Map.of(), List.of(new MetricSampleDto(Instant.now(), singleValue))))
                        : List.of(),
                hasData,
                warnings != null ? warnings : List.of()
        );
    }

    public Double getSingleValue() {
        if (!hasData || series == null || series.isEmpty()) {
            return null;
        }
        return series.get(0).getLatestValue();
    }

    public Double value() {
        return getSingleValue();
    }
}
