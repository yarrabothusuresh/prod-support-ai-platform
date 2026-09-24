package com.example.prodsupport.metrics;

import com.example.prodsupport.ai.tools.security.ApplicationAccessValidator;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.metrics.client.MetricsQueryClient;
import com.example.prodsupport.metrics.config.MetricsProperties;
import com.example.prodsupport.metrics.model.PrometheusAlertDto;
import com.example.prodsupport.metrics.service.ApplicationMetricsConfigService;
import com.example.prodsupport.metrics.service.PrometheusAlertService;
import com.example.prodsupport.metrics.service.PrometheusAlertService.ActiveAlertsResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrometheusAlertServiceTest {

    @Mock
    private ApplicationAccessValidator accessValidator;

    @Mock
    private ApplicationMetricsConfigService configService;

    @Mock
    private MetricsQueryClient queryClient;

    private MetricsProperties properties;
    private PrometheusAlertService alertService;
    private RegisteredApplication paymentApp;

    @BeforeEach
    void setUp() {
        properties = new MetricsProperties();
        properties.setEnabled(true);

        alertService = new PrometheusAlertService(
                accessValidator,
                configService,
                queryClient,
                properties
        );

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
    }

    @Test
    @DisplayName("Should filter active alerts matching application name and environment")
    void shouldFilterActiveAlertsForApplication() {
        when(accessValidator.validateAndGet("payment-service", "local")).thenReturn(paymentApp);
        when(configService.isMetricsConfiguredAndEnabled(paymentApp)).thenReturn(true);
        when(queryClient.isAvailable()).thenReturn(true);

        PrometheusAlertDto alert1 = new PrometheusAlertDto(
                "HighHttpErrorRate",
                "firing",
                "critical",
                "HTTP error rate is 8.5%",
                "HTTP 5xx rate exceeded 5%",
                "payment-service",
                "local",
                Instant.now(),
                Map.of("application", "payment-service", "environment", "local")
        );
        PrometheusAlertDto alert2 = new PrometheusAlertDto(
                "HighHttpLatency",
                "firing",
                "warning",
                "Notification latency elevated",
                "Latency > 500ms",
                "notification-service",
                "local",
                Instant.now(),
                Map.of("application", "notification-service", "environment", "local")
        );
        PrometheusAlertDto alert3 = new PrometheusAlertDto(
                "HighJvmHeapUsage",
                "firing",
                "critical",
                "Prod payment heap high",
                "Heap > 85%",
                "payment-service",
                "prod",
                Instant.now(),
                Map.of("application", "payment-service", "environment", "prod")
        );

        when(queryClient.getAlerts()).thenReturn(List.of(alert1, alert2, alert3));

        ActiveAlertsResult result = alertService.getActiveAlerts("payment-service", "local");

        assertThat(result.applicationName()).isEqualTo("payment-service");
        assertThat(result.environment()).isEqualTo("local");
        assertThat(result.activeAlerts()).hasSize(1);
        assertThat(result.activeAlerts().get(0).alertName()).isEqualTo("HighHttpErrorRate");
    }

    @Test
    @DisplayName("Should return warning when Prometheus is unreachable")
    void shouldReturnWarningWhenPrometheusUnreachable() {
        when(accessValidator.validateAndGet("payment-service", "local")).thenReturn(paymentApp);
        when(configService.isMetricsConfiguredAndEnabled(paymentApp)).thenReturn(true);
        when(queryClient.isAvailable()).thenReturn(false);

        ActiveAlertsResult result = alertService.getActiveAlerts("payment-service", "local");

        assertThat(result.activeAlerts()).isEmpty();
        assertThat(result.warnings()).anyMatch(w -> w.contains("Prometheus server is currently unreachable"));
    }
}
