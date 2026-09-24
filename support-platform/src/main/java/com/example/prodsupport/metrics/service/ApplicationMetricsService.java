package com.example.prodsupport.metrics.service;

import com.example.prodsupport.ai.tools.security.ApplicationAccessValidator;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.metrics.client.MetricsQueryClient;
import com.example.prodsupport.metrics.config.MetricsProperties;
import com.example.prodsupport.metrics.dto.ApplicationMetricsSummaryDto;
import com.example.prodsupport.metrics.dto.ApplicationMetricsSummaryDto.*;
import com.example.prodsupport.metrics.entity.ApplicationMetricsConfigEntity;
import com.example.prodsupport.metrics.model.MetricInstantResult;
import com.example.prodsupport.metrics.model.MetricRangeResult;
import com.example.prodsupport.metrics.query.MetricQueryTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class ApplicationMetricsService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationMetricsService.class);

    private final ApplicationAccessValidator accessValidator;
    private final ApplicationMetricsConfigService configService;
    private final MetricsQueryClient queryClient;
    private final MetricsProperties properties;
    private final TrendAnalysisService trendAnalysisService;

    public ApplicationMetricsService(ApplicationAccessValidator accessValidator,
                                     ApplicationMetricsConfigService configService,
                                     MetricsQueryClient queryClient,
                                     MetricsProperties properties,
                                     TrendAnalysisService trendAnalysisService) {
        this.accessValidator = accessValidator;
        this.configService = configService;
        this.queryClient = queryClient;
        this.properties = properties;
        this.trendAnalysisService = trendAnalysisService;
    }

    public boolean isMetricsConfiguredAndEnabled(RegisteredApplication app) {
        return configService.isMetricsConfiguredAndEnabled(app);
    }

    public ApplicationMetricsSummaryDto getMetricsSummary(String applicationName, String environment, int windowMinutes) {
        RegisteredApplication app = accessValidator.validateAndGet(applicationName, environment);
        return getMetricsSummary(app, windowMinutes);
    }

    public ApplicationMetricsSummaryDto getMetricsSummary(RegisteredApplication app, int windowMinutes) {
        int boundedWindow = Math.min(Math.max(1, windowMinutes), properties.getMaximumWindowHours() * 60);
        List<String> warnings = new ArrayList<>();

        if (!properties.isEnabled() || !configService.isMetricsConfiguredAndEnabled(app)) {
            warnings.add("Metrics collection is disabled for application '" + app.getApplicationName() + "'");
            return new ApplicationMetricsSummaryDto(
                    app.getApplicationName(),
                    app.getEnvironment(),
                    boundedWindow,
                    new ApplicationAvailabilityDto(false, null, "DISABLED"),
                    null, null, null, null,
                    warnings
            );
        }

        if (!queryClient.isAvailable()) {
            warnings.add("Prometheus metrics provider is currently unavailable at " + queryClient.getEndpointUrl());
            return new ApplicationMetricsSummaryDto(
                    app.getApplicationName(),
                    app.getEnvironment(),
                    boundedWindow,
                    new ApplicationAvailabilityDto(false, null, "UNAVAILABLE"),
                    null, null, null, null,
                    warnings
            );
        }

        ApplicationMetricsConfigEntity config = configService.findEntityByApplication(app)
                .orElseGet(() -> new ApplicationMetricsConfigEntity(app, true, app.getApplicationName(), app.getApplicationName()));

        String job = config.getPrometheusJob();
        String label = config.getApplicationLabel();

        // 1. Availability
        ApplicationAvailabilityDto availability = getAvailability(job, warnings);

        // 2. HTTP Metrics
        HttpMetricsDto http = getHttpMetrics(label, boundedWindow, warnings);

        // 3. JVM Metrics
        JvmMetricsDto jvm = getJvmMetrics(label, warnings);

        // 4. Resource / CPU Metrics
        CpuMetricsDto cpu = getCpuMetrics(label, warnings);

        // 5. Database Pool Metrics
        DatabasePoolMetricsDto dbPool = getDatabasePoolMetrics(label, warnings);

        return new ApplicationMetricsSummaryDto(
                app.getApplicationName(),
                app.getEnvironment(),
                boundedWindow,
                availability,
                http,
                jvm,
                cpu,
                dbPool,
                warnings
        );
    }

    public ApplicationAvailabilityDto getAvailability(String jobName, List<String> warnings) {
        try {
            String query = MetricQueryTemplate.APPLICATION_AVAILABILITY.buildQuery(jobName);
            MetricInstantResult result = queryClient.queryInstant(query, Instant.now());

            if (result.hasData()) {
                Double val = result.getSingleValue();
                boolean isUp = val != null && val >= 1.0;
                return new ApplicationAvailabilityDto(isUp, isUp ? 100.0 : 0.0, isUp ? "UP" : "DOWN");
            }
            return new ApplicationAvailabilityDto(false, null, "NO_DATA");
        } catch (Exception ex) {
            log.warn("Failed to get availability for job '{}': {}", jobName, ex.getMessage());
            warnings.add("Availability metric query error: " + ex.getMessage());
            return new ApplicationAvailabilityDto(false, null, "ERROR");
        }
    }

    public HttpMetricsDto getHttpMetrics(String applicationLabel, int windowMinutes, List<String> warnings) {
        Double requestRate = null;
        Double errorRate = null;
        Double errorPercent = null;
        Double p95Latency = null;
        Double avgLatency = null;

        try {
            // Request Rate
            String reqQuery = MetricQueryTemplate.HTTP_REQUEST_RATE.buildQuery(applicationLabel, windowMinutes);
            MetricInstantResult reqResult = queryClient.queryInstant(reqQuery, Instant.now());
            if (reqResult.hasData() && reqResult.getSingleValue() != null) {
                requestRate = roundTwoDecimals(reqResult.getSingleValue());
            }

            // Error Rate
            String errQuery = MetricQueryTemplate.HTTP_ERROR_RATE.buildQuery(applicationLabel, windowMinutes);
            MetricInstantResult errResult = queryClient.queryInstant(errQuery, Instant.now());
            if (errResult.hasData() && errResult.getSingleValue() != null) {
                errorRate = roundTwoDecimals(errResult.getSingleValue());
            }

            // Compute Error Percentage (safe against division by zero)
            if (requestRate != null && requestRate > 0) {
                double safeErr = errorRate != null ? errorRate : 0.0;
                errorPercent = roundTwoDecimals(Math.min(100.0, (safeErr / requestRate) * 100.0));
            } else if (requestRate != null && requestRate == 0.0) {
                errorPercent = 0.0;
            }

            // p95 Latency
            String p95Query = MetricQueryTemplate.HTTP_LATENCY_P95.buildQuery(applicationLabel, windowMinutes);
            MetricInstantResult p95Result = queryClient.queryInstant(p95Query, Instant.now());
            if (p95Result.hasData() && p95Result.getSingleValue() != null && !Double.isNaN(p95Result.getSingleValue())) {
                p95Latency = roundTwoDecimals(p95Result.getSingleValue());
            }

            // Avg Latency
            String avgQuery = MetricQueryTemplate.HTTP_LATENCY_AVG.buildQuery(applicationLabel, windowMinutes);
            MetricInstantResult avgResult = queryClient.queryInstant(avgQuery, Instant.now());
            if (avgResult.hasData() && avgResult.getSingleValue() != null && !Double.isNaN(avgResult.getSingleValue())) {
                avgLatency = roundTwoDecimals(avgResult.getSingleValue());
            }

        } catch (Exception ex) {
            log.warn("Failed to retrieve HTTP metrics for '{}': {}", applicationLabel, ex.getMessage());
            warnings.add("HTTP metrics partial failure: " + ex.getMessage());
        }

        return new HttpMetricsDto(requestRate, errorRate, errorPercent, p95Latency, avgLatency);
    }

    public JvmMetricsDto getJvmMetrics(String applicationLabel, List<String> warnings) {
        Double heapUsedMb = null;
        Double heapMaxMb = null;
        Double heapUtil = null;
        Double nonHeapMb = null;

        try {
            // Heap Used
            String usedQ = MetricQueryTemplate.JVM_HEAP_USED.buildQuery(applicationLabel);
            MetricInstantResult usedRes = queryClient.queryInstant(usedQ, Instant.now());
            if (usedRes.hasData() && usedRes.getSingleValue() != null) {
                heapUsedMb = roundTwoDecimals(usedRes.getSingleValue() / (1024.0 * 1024.0));
            }

            // Heap Max
            String maxQ = MetricQueryTemplate.JVM_HEAP_MAX.buildQuery(applicationLabel);
            MetricInstantResult maxRes = queryClient.queryInstant(maxQ, Instant.now());
            if (maxRes.hasData() && maxRes.getSingleValue() != null && maxRes.getSingleValue() > 0) {
                heapMaxMb = roundTwoDecimals(maxRes.getSingleValue() / (1024.0 * 1024.0));
            }

            // Utilization %
            if (heapUsedMb != null && heapMaxMb != null && heapMaxMb > 0) {
                heapUtil = roundTwoDecimals((heapUsedMb / heapMaxMb) * 100.0);
            }

            // Non-Heap Used
            String nonHeapQ = MetricQueryTemplate.JVM_NON_HEAP_USED.buildQuery(applicationLabel);
            MetricInstantResult nonHeapRes = queryClient.queryInstant(nonHeapQ, Instant.now());
            if (nonHeapRes.hasData() && nonHeapRes.getSingleValue() != null) {
                nonHeapMb = roundTwoDecimals(nonHeapRes.getSingleValue() / (1024.0 * 1024.0));
            }

        } catch (Exception ex) {
            log.warn("Failed to retrieve JVM metrics for '{}': {}", applicationLabel, ex.getMessage());
            warnings.add("JVM metrics partial failure: " + ex.getMessage());
        }

        return new JvmMetricsDto(heapUsedMb, heapMaxMb, heapUtil, nonHeapMb);
    }

    public CpuMetricsDto getCpuMetrics(String applicationLabel, List<String> warnings) {
        Double procCpu = null;
        Double sysCpu = null;

        try {
            String procQ = MetricQueryTemplate.PROCESS_CPU.buildQuery(applicationLabel);
            MetricInstantResult procRes = queryClient.queryInstant(procQ, Instant.now());
            if (procRes.hasData() && procRes.getSingleValue() != null && !Double.isNaN(procRes.getSingleValue())) {
                procCpu = roundTwoDecimals(procRes.getSingleValue());
            }

            String sysQ = MetricQueryTemplate.SYSTEM_CPU.buildQuery(applicationLabel);
            MetricInstantResult sysRes = queryClient.queryInstant(sysQ, Instant.now());
            if (sysRes.hasData() && sysRes.getSingleValue() != null && !Double.isNaN(sysRes.getSingleValue())) {
                sysCpu = roundTwoDecimals(sysRes.getSingleValue());
            }
        } catch (Exception ex) {
            log.warn("Failed to retrieve CPU metrics for '{}': {}", applicationLabel, ex.getMessage());
            warnings.add("CPU metrics partial failure: " + ex.getMessage());
        }

        return new CpuMetricsDto(procCpu, sysCpu);
    }

    public DatabasePoolMetricsDto getDatabasePoolMetrics(String applicationLabel, List<String> warnings) {
        Integer active = null;
        Integer idle = null;
        Integer pending = null;
        Integer max = null;
        Double util = null;

        try {
            String actQ = MetricQueryTemplate.HIKARI_ACTIVE_CONNECTIONS.buildQuery(applicationLabel);
            MetricInstantResult actRes = queryClient.queryInstant(actQ, Instant.now());
            if (actRes.hasData() && actRes.getSingleValue() != null) {
                active = (int) Math.round(actRes.getSingleValue());
            }

            String idleQ = MetricQueryTemplate.HIKARI_IDLE_CONNECTIONS.buildQuery(applicationLabel);
            MetricInstantResult idleRes = queryClient.queryInstant(idleQ, Instant.now());
            if (idleRes.hasData() && idleRes.getSingleValue() != null) {
                idle = (int) Math.round(idleRes.getSingleValue());
            }

            String pendQ = MetricQueryTemplate.HIKARI_PENDING_CONNECTIONS.buildQuery(applicationLabel);
            MetricInstantResult pendRes = queryClient.queryInstant(pendQ, Instant.now());
            if (pendRes.hasData() && pendRes.getSingleValue() != null) {
                pending = (int) Math.round(pendRes.getSingleValue());
            }

            String maxQ = MetricQueryTemplate.HIKARI_MAX_CONNECTIONS.buildQuery(applicationLabel);
            MetricInstantResult maxRes = queryClient.queryInstant(maxQ, Instant.now());
            if (maxRes.hasData() && maxRes.getSingleValue() != null) {
                max = (int) Math.round(maxRes.getSingleValue());
            }

            if (active != null && max != null && max > 0) {
                util = roundTwoDecimals(((double) active / max) * 100.0);
            }
        } catch (Exception ex) {
            log.warn("Failed to retrieve HikariCP pool metrics for '{}': {}", applicationLabel, ex.getMessage());
            warnings.add("HikariCP pool metrics partial failure: " + ex.getMessage());
        }

        return new DatabasePoolMetricsDto(active, idle, pending, max, util);
    }

    public MetricRangeResult queryMetricRange(RegisteredApplication app, MetricQueryTemplate template, int windowMinutes, Duration step) {
        ApplicationMetricsConfigEntity config = configService.findEntityByApplication(app)
                .orElseGet(() -> new ApplicationMetricsConfigEntity(app, true, app.getApplicationName(), app.getApplicationName()));

        String query = template.buildQuery(config.getApplicationLabel(), windowMinutes);
        Instant end = Instant.now();
        Instant start = end.minus(Duration.ofMinutes(windowMinutes));

        return queryClient.queryRange(query, start, end, step);
    }

    private double roundTwoDecimals(double val) {
        return Math.round(val * 100.0) / 100.0;
    }
}
