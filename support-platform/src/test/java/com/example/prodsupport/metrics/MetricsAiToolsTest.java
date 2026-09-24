package com.example.prodsupport.metrics;

import com.example.prodsupport.ai.tools.GetActiveAlertsAiTool;
import com.example.prodsupport.ai.tools.GetApplicationMetricsAiTool;
import com.example.prodsupport.ai.tools.GetHttpMetricsAiTool;
import com.example.prodsupport.ai.tools.GetJvmMetricsAiTool;
import com.example.prodsupport.ai.tools.GetResourceMetricsAiTool;
import com.example.prodsupport.ai.tools.audit.ToolExecutionAuditor;
import com.example.prodsupport.ai.tools.context.InvestigationContext;
import com.example.prodsupport.ai.tools.context.InvestigationContextHolder;
import com.example.prodsupport.ai.tools.model.GetActiveAlertsRequest;
import com.example.prodsupport.ai.tools.model.GetApplicationMetricsRequest;
import com.example.prodsupport.ai.tools.model.GetHttpMetricsRequest;
import com.example.prodsupport.ai.tools.model.GetJvmMetricsRequest;
import com.example.prodsupport.ai.tools.model.GetResourceMetricsRequest;
import com.example.prodsupport.ai.tools.security.ToolAllowlist;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.infrastructure.repository.RegisteredApplicationRepository;
import com.example.prodsupport.metrics.client.MetricsQueryClient;
import com.example.prodsupport.metrics.dto.UpdateMetricsConfigRequest;
import com.example.prodsupport.metrics.model.MetricInstantResult;
import com.example.prodsupport.metrics.model.PrometheusAlertDto;
import com.example.prodsupport.metrics.service.ApplicationMetricsConfigService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.when;

@SpringBootTest
class MetricsAiToolsTest {

    @Autowired
    private GetApplicationMetricsAiTool applicationMetricsTool;

    @Autowired
    private GetHttpMetricsAiTool httpMetricsTool;

    @Autowired
    private GetJvmMetricsAiTool jvmMetricsTool;

    @Autowired
    private GetResourceMetricsAiTool resourceMetricsTool;

    @Autowired
    private GetActiveAlertsAiTool activeAlertsTool;

    @Autowired
    private ToolExecutionAuditor auditor;

    @Autowired
    private RegisteredApplicationRepository applicationRepository;

    @Autowired
    private ApplicationMetricsConfigService configService;

    @MockBean
    private MetricsQueryClient metricsQueryClient;

    private RegisteredApplication testApp;

    @BeforeEach
    void setUp() {
        auditor.clearHistory();
        applicationRepository.deleteAll();

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
        testApp = applicationRepository.save(testApp);

        configService.updateConfig(testApp.getId(), new UpdateMetricsConfigRequest(
                true,
                "payment-service",
                "payment-service"
        ));

        when(metricsQueryClient.isAvailable()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        InvestigationContextHolder.clearContext();
    }

    @Test
    @DisplayName("GetApplicationMetricsAiTool returns golden signals and audits execution")
    void shouldExecuteApplicationMetricsTool() {
        InvestigationContext context = new InvestigationContext("payment-service", "local", "status?", 10);
        InvestigationContextHolder.setContext(context);

        when(metricsQueryClient.queryInstant(contains("up{"), any())).thenReturn(new MetricInstantResult(1.0, true, List.of()));
        when(metricsQueryClient.queryInstant(contains("http_server_requests_seconds_count"), any())).thenReturn(new MetricInstantResult(10.0, true, List.of()));

        var result = applicationMetricsTool.apply(new GetApplicationMetricsRequest("payment-service", "local", 5));

        assertThat(result.success()).isTrue();
        assertThat(result.data().applicationName()).isEqualTo("payment-service");
        assertThat(result.data().availability().available()).isTrue();
        assertThat(context.getToolsUsed()).contains(ToolAllowlist.TOOL_GET_APPLICATION_METRICS);
        assertThat(auditor.getAuditHistory()).hasSize(1);
    }

    @Test
    @DisplayName("GetHttpMetricsAiTool returns HTTP rate, error rate and latency percentiles")
    void shouldExecuteHttpMetricsTool() {
        InvestigationContext context = new InvestigationContext("payment-service", "local", "slow requests?", 10);
        InvestigationContextHolder.setContext(context);

        when(metricsQueryClient.queryInstant(contains("http_server_requests_seconds_count"), any())).thenReturn(new MetricInstantResult(15.0, true, List.of()));
        when(metricsQueryClient.queryInstant(contains("status=~\"5..\""), any())).thenReturn(new MetricInstantResult(1.5, true, List.of()));
        when(metricsQueryClient.queryInstant(contains("histogram_quantile(0.95"), any())).thenReturn(new MetricInstantResult(250.0, true, List.of()));

        var result = httpMetricsTool.apply(new GetHttpMetricsRequest("payment-service", "local", 10));

        assertThat(result.success()).isTrue();
        assertThat(result.data().requestRatePerSecond()).isEqualTo(15.0);
        assertThat(result.data().errorPercentage()).isEqualTo(10.0);
        assertThat(result.data().p95LatencyMs()).isEqualTo(250.0);
        assertThat(context.getToolsUsed()).contains(ToolAllowlist.TOOL_GET_HTTP_METRICS);
    }

    @Test
    @DisplayName("GetJvmMetricsAiTool returns memory heap, max, and utilization")
    void shouldExecuteJvmMetricsTool() {
        InvestigationContext context = new InvestigationContext("payment-service", "local", "memory leak?", 10);
        InvestigationContextHolder.setContext(context);

        when(metricsQueryClient.queryInstant(contains("jvm_memory_used_bytes"), any())).thenReturn(new MetricInstantResult(268435456.0, true, List.of()));
        when(metricsQueryClient.queryInstant(contains("jvm_memory_max_bytes"), any())).thenReturn(new MetricInstantResult(536870912.0, true, List.of()));

        var result = jvmMetricsTool.apply(new GetJvmMetricsRequest("payment-service", "local"));

        assertThat(result.success()).isTrue();
        assertThat(result.data().heapUsedMb()).isEqualTo(256.0);
        assertThat(result.data().heapMaxMb()).isEqualTo(512.0);
        assertThat(result.data().heapUtilizationPercent()).isEqualTo(50.0);
        assertThat(context.getToolsUsed()).contains(ToolAllowlist.TOOL_GET_JVM_METRICS);
    }

    @Test
    @DisplayName("GetResourceMetricsAiTool returns CPU and DB connection pool stats")
    void shouldExecuteResourceMetricsTool() {
        InvestigationContext context = new InvestigationContext("payment-service", "local", "cpu spike?", 10);
        InvestigationContextHolder.setContext(context);

        when(metricsQueryClient.queryInstant(contains("process_cpu_usage"), any())).thenReturn(new MetricInstantResult(20.0, true, List.of()));
        when(metricsQueryClient.queryInstant(contains("hikaricp_connections_active"), any())).thenReturn(new MetricInstantResult(5.0, true, List.of()));
        when(metricsQueryClient.queryInstant(contains("hikaricp_connections_max"), any())).thenReturn(new MetricInstantResult(10.0, true, List.of()));

        var result = resourceMetricsTool.apply(new GetResourceMetricsRequest("payment-service", "local"));

        assertThat(result.success()).isTrue();
        assertThat(result.data().processCpuPercent()).isEqualTo(20.0);
        assertThat(context.getToolsUsed()).contains(ToolAllowlist.TOOL_GET_RESOURCE_METRICS);
    }

    @Test
    @DisplayName("GetActiveAlertsAiTool returns firing Prometheus alerts")
    void shouldExecuteActiveAlertsTool() {
        InvestigationContext context = new InvestigationContext("payment-service", "local", "alerts firing?", 10);
        InvestigationContextHolder.setContext(context);

        PrometheusAlertDto alert = new PrometheusAlertDto(
                "HighHttpErrorRate",
                "firing",
                "critical",
                "5xx error rate > 5%",
                "Elevated 5xx errors",
                "payment-service",
                "local",
                Instant.now(),
                Map.of("application", "payment-service", "environment", "local")
        );
        when(metricsQueryClient.getAlerts()).thenReturn(List.of(alert));

        var result = activeAlertsTool.apply(new GetActiveAlertsRequest("payment-service", "local"));

        assertThat(result.success()).isTrue();
        assertThat(result.data().activeAlerts()).hasSize(1);
        assertThat(result.data().activeAlerts().get(0).alertName()).isEqualTo("HighHttpErrorRate");
        assertThat(context.getToolsUsed()).contains(ToolAllowlist.TOOL_GET_ACTIVE_ALERTS);
    }

    @Test
    @DisplayName("Tool execution limit is enforced when maxToolCalls is exhausted")
    void shouldEnforceMaxToolCallsLimit() {
        InvestigationContext context = new InvestigationContext("payment-service", "local", "status?", 1);
        InvestigationContextHolder.setContext(context);

        when(metricsQueryClient.queryInstant(contains("up{"), any())).thenReturn(new MetricInstantResult(1.0, true, List.of()));

        // Call 1 - should succeed
        applicationMetricsTool.apply(new GetApplicationMetricsRequest("payment-service", "local", 5));

        // Call 2 - should be blocked
        var blockedResult = applicationMetricsTool.apply(new GetApplicationMetricsRequest("payment-service", "local", 5));

        assertThat(blockedResult.success()).isFalse();
        assertThat(blockedResult.warning()).contains("maximum diagnostic steps");
    }
}
