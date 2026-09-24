package com.example.prodsupport.metrics;

import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.infrastructure.repository.RegisteredApplicationRepository;
import com.example.prodsupport.metrics.client.MetricsQueryClient;
import com.example.prodsupport.metrics.dto.UpdateMetricsConfigRequest;
import com.example.prodsupport.metrics.model.MetricInstantResult;
import com.example.prodsupport.metrics.model.PrometheusAlertDto;
import com.example.prodsupport.metrics.service.ApplicationMetricsConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApplicationMetricsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegisteredApplicationRepository applicationRepository;

    @Autowired
    private ApplicationMetricsConfigService configService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MetricsQueryClient queryClient;

    private RegisteredApplication testApp;

    @BeforeEach
    void setUp() {
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

        when(queryClient.isAvailable()).thenReturn(true);
        when(queryClient.getProviderName()).thenReturn("Prometheus");
        when(queryClient.getEndpointUrl()).thenReturn("http://localhost:9090");
    }

    @Test
    @DisplayName("GET /api/applications/{id}/diagnostics/metrics should return metrics summary")
    void shouldReturnMetricsSummary() throws Exception {
        when(queryClient.queryInstant(contains("up{"), any())).thenReturn(new MetricInstantResult(1.0, true, List.of()));
        when(queryClient.queryInstant(contains("http_server_requests_seconds_count"), any())).thenReturn(new MetricInstantResult(20.0, true, List.of()));

        mockMvc.perform(get("/api/applications/" + testApp.getId() + "/diagnostics/metrics")
                        .param("windowMinutes", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationName").value("payment-service"))
                .andExpect(jsonPath("$.windowMinutes").value(5))
                .andExpect(jsonPath("$.availability.available").value(true));
    }

    @Test
    @DisplayName("PUT /api/applications/{id}/metrics should update config")
    void shouldUpdateMetricsConfig() throws Exception {
        UpdateMetricsConfigRequest updateReq = new UpdateMetricsConfigRequest(
                true,
                "custom-prom-job",
                "payment-service"
        );

        mockMvc.perform(put("/api/applications/" + testApp.getId() + "/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prometheusJob").value("custom-prom-job"))
                .andExpect(jsonPath("$.applicationLabel").value("payment-service"));
    }

    @Test
    @DisplayName("POST /api/applications/{id}/metrics/query should execute safe controlled query")
    void shouldExecuteControlledQuery() throws Exception {
        when(queryClient.queryInstant(anyString(), any())).thenReturn(new MetricInstantResult(15.5, true, List.of()));

        String reqJson = """
                {
                  "metricType": "HTTP_REQUEST_RATE",
                  "minutes": 5
                }
                """;

        mockMvc.perform(post("/api/applications/" + testApp.getId() + "/metrics/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationName").value("payment-service"))
                .andExpect(jsonPath("$.metricType").value("HTTP_REQUEST_RATE"))
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    @DisplayName("GET /api/applications/{id}/alerts should return active alerts")
    void shouldReturnActiveAlerts() throws Exception {
        PrometheusAlertDto alert = new PrometheusAlertDto(
                "HighHttpLatency",
                "firing",
                "warning",
                "Latency > 500ms",
                "High latency on payment-service",
                "payment-service",
                "local",
                Instant.now(),
                Map.of("application", "payment-service", "environment", "local")
        );
        when(queryClient.getAlerts()).thenReturn(List.of(alert));

        mockMvc.perform(get("/api/applications/" + testApp.getId() + "/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationName").value("payment-service"))
                .andExpect(jsonPath("$.activeAlerts", hasSize(1)))
                .andExpect(jsonPath("$.activeAlerts[0].name").value("HighHttpLatency"));
    }

    @Test
    @DisplayName("GET /api/metrics/status should return provider status")
    void shouldReturnProviderStatus() throws Exception {
        mockMvc.perform(get("/api/metrics/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.provider").value("Prometheus"));
    }
}
