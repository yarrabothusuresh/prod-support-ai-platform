package com.example.prodsupport.metrics;

import com.example.prodsupport.application.dto.SupportInvestigationRequest;
import com.example.prodsupport.application.dto.SupportInvestigationResponse;
import com.example.prodsupport.application.service.InvestigationService;
import com.example.prodsupport.database.model.*;
import com.example.prodsupport.database.service.ApplicationDatabaseDiagnosticService;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.infrastructure.repository.RegisteredApplicationRepository;
import com.example.prodsupport.logging.client.LogSearchClient;
import com.example.prodsupport.logging.model.ErrorPatternDto;
import com.example.prodsupport.logging.model.ErrorPatternResult;
import com.example.prodsupport.logging.model.LogEntryDto;
import com.example.prodsupport.logging.model.LogSearchResult;
import com.example.prodsupport.logging.service.ErrorPatternService;
import com.example.prodsupport.logging.service.LogSearchService;
import com.example.prodsupport.metrics.client.MetricsQueryClient;
import com.example.prodsupport.metrics.dto.UpdateMetricsConfigRequest;
import com.example.prodsupport.metrics.model.MetricInstantResult;
import com.example.prodsupport.metrics.model.PrometheusAlertDto;
import com.example.prodsupport.metrics.service.ApplicationMetricsConfigService;
import com.example.prodsupport.tracing.client.TraceSearchClient;
import com.example.prodsupport.tracing.dto.TraceSummaryDto;
import com.example.prodsupport.tracing.model.TraceSearchResult;
import com.example.prodsupport.tracing.service.TraceSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class CrossSystemMetricsInvestigationTest {

    @Autowired
    private InvestigationService investigationService;

    @Autowired
    private RegisteredApplicationRepository applicationRepository;

    @Autowired
    private ApplicationMetricsConfigService configService;

    @MockBean
    private ChatModel chatModel;

    @MockBean
    private MetricsQueryClient metricsQueryClient;

    @MockBean
    private ApplicationDatabaseDiagnosticService databaseDiagnosticService;

    @MockBean
    private LogSearchClient logSearchClient;

    @MockBean
    private LogSearchService logSearchService;

    @MockBean
    private ErrorPatternService errorPatternService;

    @MockBean
    private TraceSearchClient traceSearchClient;

    @MockBean
    private TraceSearchService traceSearchService;

    private RegisteredApplication paymentApp;

    @BeforeEach
    void setUp() {
        applicationRepository.deleteAll();

        paymentApp = new RegisteredApplication(
                "payment-service",
                "payments",
                "local",
                "Payment service",
                "http://localhost:8081",
                "http://localhost:8081/actuator/health",
                "http://localhost:8081/support/info",
                true
        );
        paymentApp = applicationRepository.save(paymentApp);

        configService.updateConfig(paymentApp.getId(), new UpdateMetricsConfigRequest(
                true,
                "payment-service",
                "payment-service"
        ));
    }

    @Test
    @DisplayName("Deterministic cross-system investigation correlates Metrics, Alerts, Logs, Traces, and Database")
    void shouldCorrelateMetricsWithAllPillarsInInvestigation() {
        // Mock Prometheus
        when(metricsQueryClient.isAvailable()).thenReturn(true);
        when(metricsQueryClient.queryInstant(contains("up{"), any())).thenReturn(new MetricInstantResult(1.0, true, List.of()));
        when(metricsQueryClient.queryInstant(contains("http_server_requests_seconds_count"), any())).thenReturn(new MetricInstantResult(30.0, true, List.of()));
        when(metricsQueryClient.queryInstant(contains("status=~\"5..\""), any())).thenReturn(new MetricInstantResult(4.5, true, List.of()));
        when(metricsQueryClient.queryInstant(contains("histogram_quantile(0.95"), any())).thenReturn(new MetricInstantResult(850.0, true, List.of()));
        when(metricsQueryClient.queryInstant(contains("jvm_memory_used_bytes"), any())).thenReturn(new MetricInstantResult(400000000.0, true, List.of()));
        when(metricsQueryClient.queryInstant(contains("hikaricp_connections_active"), any())).thenReturn(new MetricInstantResult(10.0, true, List.of()));

        PrometheusAlertDto alert = new PrometheusAlertDto(
                "HighHttpErrorRate",
                "firing",
                "critical",
                "High error rate on payments",
                "5xx error rate > 5%",
                "payment-service",
                "local",
                Instant.now(),
                Map.of("application", "payment-service", "environment", "local")
        );
        when(metricsQueryClient.getAlerts()).thenReturn(List.of(alert));

        // Mock Database
        when(databaseDiagnosticService.runDiagnostics(any())).thenReturn(
                new ApplicationDatabaseDiagnostics(
                        true,
                        "payments_db",
                        DatabaseType.POSTGRESQL,
                        DatabaseHealthResult.up("payments_db", DatabaseType.POSTGRESQL, 15),
                        new ConnectionPoolResult("HikariPool-1", 10, 0, 10, 10, 5, 5, 100, "CRITICAL",
                                "Connection pool exhausted", "application-endpoint", Instant.now()),
                        DatabaseActivityResult.empty(),
                        List.of("Connection pool exhausted")
                )
        );

        // Mock Centralized Logs
        when(logSearchClient.isAvailable()).thenReturn(true);
        LogEntryDto entry = new LogEntryDto(
                Instant.now().toString(), "ERROR", "DatabaseConnectionException",
                "Unable to acquire database connection", "CORR-001", "database", "Dao", null
        );
        when(logSearchService.searchLogs(anyString(), anyString(), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(new LogSearchResult("payment-service", "local", 20L, false, List.of(entry), List.of()));
        when(errorPatternService.summarizeErrors(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(new ErrorPatternResult("payment-service", "local", 15,
                        List.of(new ErrorPatternDto("DatabaseConnectionException", 20L)), List.of()));

        // Mock Tracing
        when(traceSearchClient.isAvailable()).thenReturn(true);
        when(traceSearchService.isTracingConfiguredAndEnabled(any())).thenReturn(true);
        when(traceSearchService.searchTraces(anyString(), anyString(), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(new TraceSearchResult("payment-service", "local", List.of(
                        new TraceSummaryDto("trace-xyz", "payment-service", "POST /api/v1/payments", 950L, true, 5, Instant.now().toString())
                ), List.of()));

        // Mock ChatModel response
        String aiDiagnosis = """
                {
                  "summary": "Payment service is experiencing elevated 5xx error rates correlated with database connection pool exhaustion.",
                  "observedFacts": [
                    "HTTP error rate is 15.0%",
                    "Active alert HighHttpErrorRate is firing",
                    "Hikari connection pool is 100% utilized with 5 threads waiting",
                    "Slowest trace took 950ms"
                  ],
                  "likelyCauses": [
                    "Database connection pool exhaustion causing HTTP 500 errors"
                  ],
                  "recommendedChecks": [
                    "Inspect active database queries and connection hold times"
                  ],
                  "confidence": "HIGH"
                }
                """;
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(aiDiagnosis)))));

        SupportInvestigationRequest req = new SupportInvestigationRequest(
                "payment-service",
                "local",
                "Why are payment transactions failing with 500 errors?",
                "DETERMINISTIC"
        );

        SupportInvestigationResponse response = investigationService.investigate(req);

        assertThat(response.applicationName()).isEqualTo("payment-service");
        assertThat(response.environment()).isEqualTo("local");
        assertThat(response.toolsUsed()).contains("get_application_metrics", "get_active_alerts", "check_database_connection_pool");
        assertThat(response.observedFacts()).anyMatch(f -> f.contains("Metrics summary") || f.contains("Active alert HighHttpErrorRate is firing") || f.contains("HighHttpErrorRate"));
    }

    @Test
    @DisplayName("Investigation succeeds gracefully when Prometheus is down without failing other diagnostic pillars")
    void shouldHandlePrometheusOutageGracefully() {
        // Prometheus unreachable
        when(metricsQueryClient.isAvailable()).thenReturn(false);

        // Database healthy
        when(databaseDiagnosticService.runDiagnostics(any())).thenReturn(
                new ApplicationDatabaseDiagnostics(
                        true,
                        "payments_db",
                        DatabaseType.POSTGRESQL,
                        DatabaseHealthResult.up("payments_db", DatabaseType.POSTGRESQL, 5),
                        new ConnectionPoolResult("HikariPool-1", 2, 8, 10, 10, 0, 0, 20, "HEALTHY",
                                "Connection pool healthy", "application-endpoint", Instant.now()),
                        DatabaseActivityResult.empty(),
                        List.of()
                )
        );

        String aiDiagnosis = """
                {
                  "summary": "Application database is healthy.",
                  "observedFacts": [
                    "Database status is UP",
                    "Connection pool is HEALTHY"
                  ],
                  "likelyCauses": [],
                  "recommendedChecks": [
                    "Verify Prometheus connectivity"
                  ],
                  "confidence": "MEDIUM"
                }
                """;
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(aiDiagnosis)))));

        SupportInvestigationRequest req = new SupportInvestigationRequest(
                "payment-service",
                "local",
                "Check system status",
                "DETERMINISTIC"
        );

        SupportInvestigationResponse response = investigationService.investigate(req);

        assertThat(response.applicationName()).isEqualTo("payment-service");
        assertThat(response.warnings()).anyMatch(w -> w.contains("Prometheus") || w.contains("Metrics"));
    }
}
