package com.example.prodsupport.metrics.client;

import com.example.prodsupport.metrics.model.MetricInstantResult;
import com.example.prodsupport.metrics.model.MetricRangeResult;
import com.example.prodsupport.metrics.model.PrometheusAlertDto;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public interface MetricsQueryClient {

    MetricInstantResult queryInstant(String query, Instant time);

    default MetricInstantResult queryInstant(String query) {
        return queryInstant(query, Instant.now());
    }

    MetricRangeResult queryRange(String query, Instant start, Instant end, Duration step);

    List<PrometheusAlertDto> getAlerts();

    default List<PrometheusAlertDto> getActiveAlerts() {
        return getAlerts();
    }

    boolean isAvailable();

    String getProviderName();

    String getEndpointUrl();
}
