package com.example.prodsupport.metrics.query;

/**
 * Predefined, typed server-side PromQL templates.
 * Enforces mandatory application and environment scope.
 * Arbitrary client or LLM PromQL expressions are strictly prohibited.
 */
public enum MetricQueryTemplate {

    APPLICATION_AVAILABILITY(
            "up{job=\"%s\"}"
    ),

    HTTP_REQUEST_RATE(
            "sum(rate(http_server_requests_seconds_count{application=\"%s\"}[%dm]))"
    ),

    HTTP_ERROR_RATE(
            "sum(rate(http_server_requests_seconds_count{application=\"%s\",status=~\"5..\"}[%dm]))"
    ),

    HTTP_LATENCY_P95(
            "histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{application=\"%s\"}[%dm]))) * 1000"
    ),

    HTTP_LATENCY_AVG(
            "(sum(rate(http_server_requests_seconds_sum{application=\"%s\"}[%dm])) / sum(rate(http_server_requests_seconds_count{application=\"%s\"}[%dm]))) * 1000"
    ),

    JVM_HEAP_USED(
            "sum(jvm_memory_used_bytes{application=\"%s\",area=\"heap\"})"
    ),

    JVM_HEAP_MAX(
            "sum(jvm_memory_max_bytes{application=\"%s\",area=\"heap\"})"
    ),

    JVM_NON_HEAP_USED(
            "sum(jvm_memory_used_bytes{application=\"%s\",area=\"nonheap\"})"
    ),

    JVM_GC_PAUSE_COUNT(
            "sum(rate(jvm_gc_pause_seconds_count{application=\"%s\"}[%dm]))"
    ),

    PROCESS_CPU(
            "process_cpu_usage{application=\"%s\"} * 100"
    ),

    SYSTEM_CPU(
            "system_cpu_usage{application=\"%s\"} * 100"
    ),

    HIKARI_ACTIVE_CONNECTIONS(
            "hikaricp_connections_active{application=\"%s\"}"
    ),

    HIKARI_IDLE_CONNECTIONS(
            "hikaricp_connections_idle{application=\"%s\"}"
    ),

    HIKARI_PENDING_CONNECTIONS(
            "hikaricp_connections_pending{application=\"%s\"}"
    ),

    HIKARI_MAX_CONNECTIONS(
            "hikaricp_connections_max{application=\"%s\"}"
    ),

    KAFKA_CONSUMER_RECORDS_CONSUMED_RATE(
            "sum(rate(kafka_consumer_records_consumed_total_records_total{application=\"%s\"}[%dm]))"
    ),

    CUSTOM_PAYMENT_REQUESTS_RATE(
            "sum(rate(payment_requests_total{application=\"%s\"}[%dm]))"
    ),

    CUSTOM_PAYMENT_SUCCESS_RATE(
            "sum(rate(payment_success_total{application=\"%s\"}[%dm]))"
    ),

    CUSTOM_PAYMENT_FAILURE_RATE(
            "sum(rate(payment_failure_total{application=\"%s\"}[%dm]))"
    );

    private final String template;

    MetricQueryTemplate(String template) {
        this.template = template;
    }

    public String buildQuery(String targetIdentifier) {
        String sanitized = sanitizeIdentifier(targetIdentifier);
        return String.format(template, sanitized);
    }

    public String buildQuery(String targetIdentifier, int windowMinutes) {
        String sanitized = sanitizeIdentifier(targetIdentifier);
        int boundedWindow = Math.min(Math.max(1, windowMinutes), 1440);
        // If template has multiple placeholders, format appropriately
        int formatCount = template.split("%[ds]").length - 1;
        if (formatCount == 1) {
            return String.format(template, sanitized);
        } else if (formatCount == 2) {
            return String.format(template, sanitized, boundedWindow);
        } else if (formatCount == 3) {
            return String.format(template, sanitized, boundedWindow, sanitized, boundedWindow);
        } else if (formatCount == 4) {
            return String.format(template, sanitized, boundedWindow, sanitized, boundedWindow);
        }
        return String.format(template, sanitized, boundedWindow);
    }

    public static String sanitizeIdentifier(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Target identifier cannot be null or empty");
        }
        // Only alphanumeric, hyphens, underscores are allowed in label selectors
        if (!input.matches("^[a-zA-Z0-9_-]+$")) {
            throw new IllegalArgumentException("Invalid metric label identifier: '" + input + "'. Only alphanumeric, hyphens and underscores allowed.");
        }
        return input.trim();
    }
}
