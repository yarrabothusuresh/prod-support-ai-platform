package com.example.prodsupport.metrics.telemetry;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class SupportPlatformMetrics {

    private final MeterRegistry meterRegistry;
    private final Counter toolExecutionCounter;
    private final Counter toolFailureCounter;
    private final Counter metricsQueryCounter;
    private final Counter metricsQueryFailureCounter;

    public SupportPlatformMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.toolExecutionCounter = Counter.builder("prod_support_tool_executions_total")
                .description("Total number of diagnostic tool executions in support-platform")
                .register(meterRegistry);
        this.toolFailureCounter = Counter.builder("prod_support_tool_failures_total")
                .description("Total number of diagnostic tool failures in support-platform")
                .register(meterRegistry);
        this.metricsQueryCounter = Counter.builder("prod_support_metrics_queries_total")
                .description("Total number of Prometheus metric queries executed")
                .register(meterRegistry);
        this.metricsQueryFailureCounter = Counter.builder("prod_support_metrics_query_failures_total")
                .description("Total number of Prometheus metric query failures")
                .register(meterRegistry);
    }

    public void recordToolExecution(String toolName, boolean success, long durationMs) {
        if (success) {
            toolExecutionCounter.increment();
            Counter.builder("prod_support_tool_calls_total")
                    .tag("tool", toolName)
                    .tag("status", "SUCCESS")
                    .register(meterRegistry)
                    .increment();
        } else {
            toolFailureCounter.increment();
            Counter.builder("prod_support_tool_calls_total")
                    .tag("tool", toolName)
                    .tag("status", "FAILURE")
                    .register(meterRegistry)
                    .increment();
        }
        Timer.builder("prod_support_tool_duration_seconds")
                .tag("tool", toolName)
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordMetricsQuery(String queryType, boolean success, long durationMs) {
        if (success) {
            metricsQueryCounter.increment();
        } else {
            metricsQueryFailureCounter.increment();
        }
        Timer.builder("prod_support_metrics_query_duration_seconds")
                .tag("query_type", queryType)
                .tag("status", success ? "SUCCESS" : "FAILURE")
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }
}
