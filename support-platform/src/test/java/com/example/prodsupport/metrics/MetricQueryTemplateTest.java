package com.example.prodsupport.metrics;

import com.example.prodsupport.metrics.query.MetricQueryTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetricQueryTemplateTest {

    @Test
    @DisplayName("Should generate valid up query with sanitized application and job")
    void shouldGenerateValidUpQuery() {
        String query = MetricQueryTemplate.APPLICATION_AVAILABILITY.buildQuery("payment-service");
        assertThat(query).isEqualTo("up{job=\"payment-service\"}");
    }

    @Test
    @DisplayName("Should generate valid HTTP request rate query with bounded window")
    void shouldGenerateValidHttpRequestRateQuery() {
        String query = MetricQueryTemplate.HTTP_REQUEST_RATE.buildQuery("payment-service", 5);
        assertThat(query).isEqualTo("sum(rate(http_server_requests_seconds_count{application=\"payment-service\"}[5m]))");
    }

    @Test
    @DisplayName("Should generate valid HTTP error rate query with status 5xx regex")
    void shouldGenerateValidHttpErrorRateQuery() {
        String query = MetricQueryTemplate.HTTP_ERROR_RATE.buildQuery("payment-service", 10);
        assertThat(query).isEqualTo("sum(rate(http_server_requests_seconds_count{application=\"payment-service\",status=~\"5..\"}[10m]))");
    }

    @Test
    @DisplayName("Should generate valid HTTP latency p95 query with histogram quantile")
    void shouldGenerateValidHttpLatencyP95Query() {
        String query = MetricQueryTemplate.HTTP_LATENCY_P95.buildQuery("payment-service", 15);
        assertThat(query).isEqualTo("histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{application=\"payment-service\"}[15m]))) * 1000");
    }

    @Test
    @DisplayName("Should generate valid JVM heap and CPU queries")
    void shouldGenerateValidJvmAndResourceQueries() {
        String heapUsed = MetricQueryTemplate.JVM_HEAP_USED.buildQuery("payment-service");
        assertThat(heapUsed).isEqualTo("sum(jvm_memory_used_bytes{application=\"payment-service\",area=\"heap\"})");

        String heapMax = MetricQueryTemplate.JVM_HEAP_MAX.buildQuery("payment-service");
        assertThat(heapMax).isEqualTo("sum(jvm_memory_max_bytes{application=\"payment-service\",area=\"heap\"})");

        String cpu = MetricQueryTemplate.PROCESS_CPU.buildQuery("payment-service");
        assertThat(cpu).isEqualTo("process_cpu_usage{application=\"payment-service\"} * 100");

        String dbActive = MetricQueryTemplate.HIKARI_ACTIVE_CONNECTIONS.buildQuery("payment-service");
        assertThat(dbActive).isEqualTo("hikaricp_connections_active{application=\"payment-service\"}");
    }

    @Test
    @DisplayName("Should enforce minimum window of 1 minute")
    void shouldEnforceMinimumWindow() {
        String query = MetricQueryTemplate.HTTP_REQUEST_RATE.buildQuery("payment-service", 0);
        assertThat(query).contains("[1m]");

        String queryNegative = MetricQueryTemplate.HTTP_REQUEST_RATE.buildQuery("payment-service", -5);
        assertThat(queryNegative).contains("[1m]");
    }

    @ParameterizedTest
    @ValueSource(strings = {"payment\"service", "app;drop table", "app\nname", "app\\test", "app 123"})
    @DisplayName("Should reject malicious label characters to prevent PromQL injection")
    void shouldRejectMaliciousCharacters(String maliciousApp) {
        assertThatThrownBy(() -> MetricQueryTemplate.sanitizeIdentifier(maliciousApp))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
