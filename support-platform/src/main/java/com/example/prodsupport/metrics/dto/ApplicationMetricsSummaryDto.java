package com.example.prodsupport.metrics.dto;

import java.util.List;

public record ApplicationMetricsSummaryDto(
        String applicationName,
        String environment,
        int windowMinutes,
        ApplicationAvailabilityDto availability,
        HttpMetricsDto http,
        JvmMetricsDto jvm,
        CpuMetricsDto cpu,
        DatabasePoolMetricsDto databasePool,
        List<String> warnings
) {
    public record ApplicationAvailabilityDto(
            boolean available,
            Double availabilityPercent,
            String status
    ) {}

    public record HttpMetricsDto(
            Double requestRatePerSecond,
            Double serverErrorRatePerSecond,
            Double errorPercentage,
            Double p95LatencyMs,
            Double avgLatencyMs
    ) {}

    public record JvmMetricsDto(
            Double heapUsedMb,
            Double heapMaxMb,
            Double heapUtilizationPercent,
            Double nonHeapUsedMb
    ) {}

    public record CpuMetricsDto(
            Double processCpuPercent,
            Double systemCpuPercent
    ) {}

    public record DatabasePoolMetricsDto(
            Integer activeConnections,
            Integer idleConnections,
            Integer pendingConnections,
            Integer maxConnections,
            Double utilizationPercent
    ) {}
}
