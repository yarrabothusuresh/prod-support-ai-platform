package com.example.prodsupport.metrics;

import com.example.prodsupport.metrics.client.PrometheusMetricsQueryClient;
import com.example.prodsupport.metrics.config.MetricsProperties;
import com.example.prodsupport.metrics.model.MetricInstantResult;
import com.example.prodsupport.metrics.model.MetricRangeResult;
import com.example.prodsupport.metrics.model.PrometheusAlertDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PrometheusMetricsQueryClientTest {

    private MockWebServer mockWebServer;
    private PrometheusMetricsQueryClient client;
    private MetricsProperties properties;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        properties = new MetricsProperties();
        properties.setEnabled(true);
        properties.setPrometheusBaseUrl(mockWebServer.url("/").toString());
        properties.setQueryTimeout(Duration.ofSeconds(2));
        objectMapper = new ObjectMapper();

        client = new PrometheusMetricsQueryClient(properties, objectMapper, RestClient.builder());
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("isAvailable returns true when Prometheus /-/healthy responds 200")
    void shouldReportAvailableWhenHealthy() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("Prometheus Server is Healthy."));

        assertThat(client.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("isAvailable returns false when Prometheus returns 500 or is unreachable")
    void shouldReportUnavailableWhenError() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        assertThat(client.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("queryInstant parses vector response successfully")
    void shouldParseInstantQueryResponse() {
        String body = """
                {
                  "status": "success",
                  "data": {
                    "resultType": "vector",
                    "result": [
                      {
                        "metric": { "job": "payment-service" },
                        "value": [ 1711234567.890, "1.0" ]
                      }
                    ]
                  }
                }
                """;
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body));

        MetricInstantResult result = client.queryInstant("up{job=\"payment-service\"}");

        assertThat(result.hasData()).isTrue();
        assertThat(result.value()).isEqualTo(1.0);
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    @DisplayName("queryInstant handles empty result without error")
    void shouldHandleEmptyInstantQueryResult() {
        String body = """
                {
                  "status": "success",
                  "data": {
                    "resultType": "vector",
                    "result": []
                  }
                }
                """;
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body));

        MetricInstantResult result = client.queryInstant("up{job=\"non-existent\"}");

        assertThat(result.hasData()).isFalse();
        assertThat(result.value()).isNull();
    }

    @Test
    @DisplayName("queryRange parses matrix response with time series points")
    void shouldParseRangeQueryResponse() {
        String body = """
                {
                  "status": "success",
                  "data": {
                    "resultType": "matrix",
                    "result": [
                      {
                        "metric": { "application": "payment-service" },
                        "values": [
                          [ 1711234500, "10.5" ],
                          [ 1711234560, "15.2" ],
                          [ 1711234620, "20.0" ]
                        ]
                      }
                    ]
                  }
                }
                """;
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body));

        MetricRangeResult result = client.queryRange(
                "rate(http_requests[5m])",
                Instant.now().minusSeconds(300),
                Instant.now(),
                Duration.ofSeconds(60)
        );

        assertThat(result.hasData()).isTrue();
        assertThat(result.series()).hasSize(1);
        assertThat(result.series().get(0).samples()).hasSize(3);
        assertThat(result.series().get(0).samples().get(0).value()).isEqualTo(10.5);
        assertThat(result.series().get(0).samples().get(2).value()).isEqualTo(20.0);
    }

    @Test
    @DisplayName("getActiveAlerts parses firing alerts correctly")
    void shouldParseActiveAlerts() {
        String body = """
                {
                  "status": "success",
                  "data": {
                    "alerts": [
                      {
                        "labels": {
                          "alertname": "HighHttpErrorRate",
                          "severity": "critical",
                          "application": "payment-service",
                          "environment": "local"
                        },
                        "annotations": {
                          "summary": "High HTTP 5xx error rate",
                          "description": "5xx error rate exceeded 5%"
                        },
                        "state": "firing",
                        "activeAt": "2026-09-24T00:00:00Z",
                        "value": "8.5"
                      }
                    ]
                  }
                }
                """;
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body));

        List<PrometheusAlertDto> alerts = client.getActiveAlerts();

        assertThat(alerts).hasSize(1);
        PrometheusAlertDto alert = alerts.get(0);
        assertThat(alert.alertName()).isEqualTo("HighHttpErrorRate");
        assertThat(alert.severity()).isEqualTo("critical");
        assertThat(alert.application()).isEqualTo("payment-service");
        assertThat(alert.state()).isEqualToIgnoringCase("firing");
        assertThat(alert.summary()).isEqualTo("High HTTP 5xx error rate");
    }

    @Test
    @DisplayName("Handles HTTP 500 error gracefully without throwing uncaught exceptions")
    void shouldHandle500ErrorGracefully() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("Internal Server Error"));

        MetricInstantResult result = client.queryInstant("up");

        assertThat(result.hasData()).isFalse();
        assertThat(result.warnings()).isNotEmpty();
    }
}
