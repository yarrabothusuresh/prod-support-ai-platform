package com.example.prodsupport.metrics;

import com.example.prodsupport.ai.tools.security.ApplicationAccessValidator;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.metrics.client.MetricsQueryClient;
import com.example.prodsupport.metrics.config.MetricsProperties;
import com.example.prodsupport.metrics.dto.ApplicationMetricsSummaryDto;
import com.example.prodsupport.metrics.entity.ApplicationMetricsConfigEntity;
import com.example.prodsupport.metrics.model.MetricInstantResult;
import com.example.prodsupport.metrics.service.ApplicationMetricsConfigService;
import com.example.prodsupport.metrics.service.ApplicationMetricsService;
import com.example.prodsupport.metrics.service.TrendAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationMetricsServiceTest {

    @Mock
    private ApplicationAccessValidator accessValidator;

    @Mock
    private ApplicationMetricsConfigService configService;

    @Mock
    private MetricsQueryClient queryClient;

    private MetricsProperties properties;
    private TrendAnalysisService trendAnalysisService;
    private ApplicationMetricsService metricsService;

    private RegisteredApplication testApp;

    @BeforeEach
    void setUp() {
        properties = new MetricsProperties();
        properties.setEnabled(true);
        trendAnalysisService = new TrendAnalysisService();

        metricsService = new ApplicationMetricsService(
                accessValidator,
                configService,
                queryClient,
                properties,
                trendAnalysisService
        );

        testApp = new RegisteredApplication(
                "payment-service",
                "payments",
                "local",
                "Payment service",
                "http://localhost:8081",
                "http://localhost:8081/actuator/health",
                "http://localhost:8081/support/info",
                true
        );
    }

    @Test
    @DisplayName("Should summarize golden signals when Prometheus returns full data")
    void shouldSummarizeGoldenSignalsSuccessfully() {
        when(accessValidator.validateAndGet("payment-service", "local")).thenReturn(testApp);
        when(configService.isMetricsConfiguredAndEnabled(testApp)).thenReturn(true);
        when(configService.findEntityByApplication(testApp)).thenReturn(Optional.of(
                new ApplicationMetricsConfigEntity(testApp, true, "payment-service", "payment-service")
        ));
        when(queryClient.isAvailable()).thenReturn(true);

        // up
        when(queryClient.queryInstant(contains("up{"), any())).thenReturn(new MetricInstantResult(1.0, true, List.of()));
        // request rate = 25.0 req/s
        when(queryClient.queryInstant(contains("http_server_requests_seconds_count"), any()))
                .thenReturn(new MetricInstantResult(25.0, true, List.of()));
        // error rate = 2.5 err/s (10%)
        when(queryClient.queryInstant(contains("status=~\"5..\""), any())).thenReturn(new MetricInstantResult(2.5, true, List.of()));
        // p95 latency = 120.0ms
        when(queryClient.queryInstant(contains("histogram_quantile(0.95"), any())).thenReturn(new MetricInstantResult(120.0, true, List.of()));
        // avg latency = 45.0ms
        when(queryClient.queryInstant(contains("http_server_requests_seconds_sum"), any())).thenReturn(new MetricInstantResult(45.0, true, List.of()));

        // JVM heap used = 256MB (268435456 bytes)
        when(queryClient.queryInstant(contains("jvm_memory_used_bytes"), any())).thenReturn(new MetricInstantResult(268435456.0, true, List.of()));
        // JVM heap max = 512MB (536870912 bytes)
        when(queryClient.queryInstant(contains("jvm_memory_max_bytes"), any())).thenReturn(new MetricInstantResult(536870912.0, true, List.of()));
        // JVM non-heap used = 64MB (67108864 bytes)
        when(queryClient.queryInstant(contains("area=\"nonheap\""), any())).thenReturn(new MetricInstantResult(67108864.0, true, List.of()));

        // CPU process = 15.0%
        when(queryClient.queryInstant(contains("process_cpu_usage"), any())).thenReturn(new MetricInstantResult(15.0, true, List.of()));
        // CPU system = 25.0%
        when(queryClient.queryInstant(contains("system_cpu_usage"), any())).thenReturn(new MetricInstantResult(25.0, true, List.of()));

        // DB active connections = 4
        when(queryClient.queryInstant(contains("hikaricp_connections_active"), any())).thenReturn(new MetricInstantResult(4.0, true, List.of()));
        // DB idle = 6
        when(queryClient.queryInstant(contains("hikaricp_connections_idle"), any())).thenReturn(new MetricInstantResult(6.0, true, List.of()));
        // DB pending = 0
        when(queryClient.queryInstant(contains("hikaricp_connections_pending"), any())).thenReturn(new MetricInstantResult(0.0, true, List.of()));
        // DB max = 10
        when(queryClient.queryInstant(contains("hikaricp_connections_max"), any())).thenReturn(new MetricInstantResult(10.0, true, List.of()));

        ApplicationMetricsSummaryDto summary = metricsService.getMetricsSummary("payment-service", "local", 5);

        assertThat(summary.applicationName()).isEqualTo("payment-service");
        assertThat(summary.availability().available()).isTrue();
        assertThat(summary.availability().status()).isEqualTo("UP");

        assertThat(summary.http().requestRatePerSecond()).isEqualTo(25.0);
        assertThat(summary.http().serverErrorRatePerSecond()).isEqualTo(2.5);
        assertThat(summary.http().errorPercentage()).isEqualTo(10.0);
        assertThat(summary.http().p95LatencyMs()).isEqualTo(120.0);

        assertThat(summary.jvm().heapUsedMb()).isEqualTo(256.0);
        assertThat(summary.jvm().heapMaxMb()).isEqualTo(512.0);
        assertThat(summary.jvm().heapUtilizationPercent()).isEqualTo(50.0);

        assertThat(summary.cpu().processCpuPercent()).isEqualTo(15.0);
        assertThat(summary.cpu().systemCpuPercent()).isEqualTo(25.0);

        assertThat(summary.databasePool().activeConnections()).isEqualTo(4);
        assertThat(summary.databasePool().maxConnections()).isEqualTo(10);
        assertThat(summary.databasePool().utilizationPercent()).isEqualTo(40.0);
    }

    @Test
    @DisplayName("Should avoid division by zero and return 0.0% error rate when request rate is 0.0")
    void shouldAvoidDivisionByZero() {
        when(accessValidator.validateAndGet("payment-service", "local")).thenReturn(testApp);
        when(configService.isMetricsConfiguredAndEnabled(testApp)).thenReturn(true);
        when(configService.findEntityByApplication(testApp)).thenReturn(Optional.of(
                new ApplicationMetricsConfigEntity(testApp, true, "payment-service", "payment-service")
        ));
        when(queryClient.isAvailable()).thenReturn(true);

        // Return empty by default
        when(queryClient.queryInstant(anyString(), any())).thenReturn(new MetricInstantResult(null, false, List.of()));

        when(queryClient.queryInstant(contains("up{"), any())).thenReturn(new MetricInstantResult(1.0, true, List.of()));
        when(queryClient.queryInstant(contains("http_server_requests_seconds_count"), any()))
                .thenReturn(new MetricInstantResult(0.0, true, List.of()));
        when(queryClient.queryInstant(contains("status=~\"5..\""), any())).thenReturn(new MetricInstantResult(0.0, true, List.of()));

        ApplicationMetricsSummaryDto summary = metricsService.getMetricsSummary("payment-service", "local", 5);

        assertThat(summary.http().requestRatePerSecond()).isEqualTo(0.0);
        assertThat(summary.http().errorPercentage()).isEqualTo(0.0);
        assertThat(Double.isNaN(summary.http().errorPercentage())).isFalse();
        assertThat(Double.isInfinite(summary.http().errorPercentage())).isFalse();
    }

    @Test
    @DisplayName("Should return graceful fallback when metrics is disabled for application")
    void shouldReturnDisabledSummaryWhenNotConfigured() {
        when(accessValidator.validateAndGet("payment-service", "local")).thenReturn(testApp);
        when(configService.isMetricsConfiguredAndEnabled(testApp)).thenReturn(false);

        ApplicationMetricsSummaryDto summary = metricsService.getMetricsSummary("payment-service", "local", 5);

        assertThat(summary.availability().available()).isFalse();
        assertThat(summary.availability().status()).isEqualTo("DISABLED");
        assertThat(summary.warnings()).anyMatch(w -> w.contains("Metrics collection is disabled"));
    }

    @Test
    @DisplayName("Should report UNAVAILABLE with warning when Prometheus client is unavailable")
    void shouldReportUnreachableWhenPrometheusDown() {
        when(accessValidator.validateAndGet("payment-service", "local")).thenReturn(testApp);
        when(configService.isMetricsConfiguredAndEnabled(testApp)).thenReturn(true);
        when(queryClient.isAvailable()).thenReturn(false);

        ApplicationMetricsSummaryDto summary = metricsService.getMetricsSummary("payment-service", "local", 5);

        assertThat(summary.availability().available()).isFalse();
        assertThat(summary.availability().status()).isEqualTo("UNAVAILABLE");
        assertThat(summary.warnings()).anyMatch(w -> w.contains("Prometheus metrics provider is currently unavailable"));
    }
}
