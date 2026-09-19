package com.example.prodsupport.tracing.client;

import com.example.prodsupport.tracing.config.TracingProperties;
import com.example.prodsupport.tracing.dto.TraceSummaryDto;
import com.example.prodsupport.tracing.model.TraceDetailResult;
import com.example.prodsupport.tracing.model.TraceSearchResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JaegerTraceSearchClientTest {

    private MockWebServer mockWebServer;
    private JaegerTraceSearchClient client;
    private TracingProperties properties;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        properties = new TracingProperties();
        properties.setJaegerBaseUrl(mockWebServer.url("/").toString());
        properties.setRequestTimeout(Duration.ofSeconds(2));
        properties.setSearchTimeout(Duration.ofSeconds(2));
        objectMapper = new ObjectMapper();

        client = new JaegerTraceSearchClient(properties, objectMapper, RestClient.builder());
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("isAvailable returns true when Jaeger /api/services responds 200")
    void shouldReportAvailableWhenJaegerIsUp() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"data\":[\"payment-service\"]}"));

        assertThat(client.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("isAvailable returns false when Jaeger responds with error or is unreachable")
    void shouldReportUnavailableWhenJaegerErrors() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        assertThat(client.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("searchTraces parses search response correctly")
    void shouldSearchTracesSuccessfully() {
        String json = """
                {
                  "data": [
                    {
                      "traceID": "0123456789abcdef0123456789abcdef",
                      "spans": [
                        {
                          "traceID": "0123456789abcdef0123456789abcdef",
                          "spanID": "abcdef1234567890",
                          "operationName": "POST /api/payments",
                          "startTime": 1726700000000000,
                          "duration": 450000,
                          "processID": "p1",
                          "tags": [
                            {"key": "http.method", "type": "string", "value": "POST"}
                          ]
                        }
                      ],
                      "processes": {
                        "p1": {"serviceName": "payment-service"}
                      }
                    }
                  ],
                  "total": 1
                }
                """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(json));

        TraceSearchResult result = client.searchTraces("payment-service", "local", 15, 20, false);

        assertThat(result).isNotNull();
        assertThat(result.traces()).hasSize(1);
        TraceSummaryDto trace = result.traces().get(0);
        assertThat(trace.traceId()).isEqualTo("0123456789abcdef0123456789abcdef");
        assertThat(trace.rootService()).isEqualTo("payment-service");
        assertThat(trace.operation()).isEqualTo("POST /api/payments");
        assertThat(trace.durationMs()).isEqualTo(450);
        assertThat(trace.hasError()).isFalse();
    }

    @Test
    @DisplayName("searchTraces handles empty trace result gracefully")
    void shouldHandleEmptySearchResult() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"data\":[],\"total\":0}"));

        TraceSearchResult result = client.searchTraces("payment-service", "local", 15, 20, false);
        assertThat(result.traces()).isEmpty();
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    @DisplayName("getTraceById returns populated trace detail with sanitized tags")
    void shouldGetTraceDetails() {
        String json = """
                {
                  "data": [
                    {
                      "traceID": "0123456789abcdef0123456789abcdef",
                      "spans": [
                        {
                          "traceID": "0123456789abcdef0123456789abcdef",
                          "spanID": "span001",
                          "operationName": "POST /api/payments",
                          "startTime": 1726700000000000,
                          "duration": 500000,
                          "processID": "p1",
                          "tags": [
                            {"key": "http.status_code", "type": "int64", "value": 202},
                            {"key": "user.password", "type": "string", "value": "secret123"}
                          ]
                        },
                        {
                          "traceID": "0123456789abcdef0123456789abcdef",
                          "spanID": "span002",
                          "operationName": "payment.persist",
                          "startTime": 1726700000100000,
                          "duration": 300000,
                          "processID": "p1",
                          "references": [
                            {"refType": "CHILD_OF", "spanID": "span001"}
                          ],
                          "tags": [
                            {"key": "db.system", "type": "string", "value": "postgresql"}
                          ]
                        }
                      ],
                      "processes": {
                        "p1": {"serviceName": "payment-service"}
                      }
                    }
                  ]
                }
                """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(json));

        Optional<TraceDetailResult> opt = client.getTraceById("0123456789abcdef0123456789abcdef");

        assertThat(opt).isPresent();
        TraceDetailResult detail = opt.get();
        assertThat(detail.traceId()).isEqualTo("0123456789abcdef0123456789abcdef");
        assertThat(detail.durationMs()).isEqualTo(500);
        assertThat(detail.spans()).hasSize(2);

        // Verify sensitive tag excluded
        assertThat(detail.spans().get(0).tags()).doesNotContainKey("user.password");
        assertThat(detail.spans().get(0).tags()).containsKey("http.status_code");
    }

    @Test
    @DisplayName("getTraceById rejects malformed trace ID")
    void shouldRejectMalformedTraceId() {
        assertThatThrownBy(() -> client.getTraceById("invalid-id"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid traceId format");
    }

    @Test
    @DisplayName("getTraceById returns empty optional when 404 or empty data")
    void shouldReturnEmptyWhenTraceNotFound() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"data\":[]}"));

        Optional<TraceDetailResult> opt = client.getTraceById("0123456789abcdef0123456789abcdef");
        assertThat(opt).isEmpty();
    }

    @Test
    @DisplayName("Jaeger outage does not throw unhandled exception but returns warning")
    void shouldHandleJaegerOutageGracefully() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(503));

        TraceSearchResult result = client.searchTraces("payment-service", "local", 15, 20, false);
        assertThat(result.traces()).isEmpty();
        assertThat(result.warnings()).isNotEmpty();
        assertThat(result.warnings().get(0)).contains("Distributed tracing (Jaeger) is currently unavailable");
    }
}
