package com.example.prodsupport.metrics.client;

import com.example.prodsupport.metrics.config.MetricsProperties;
import com.example.prodsupport.metrics.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Component
public class PrometheusMetricsQueryClient implements MetricsQueryClient {

    private static final Logger log = LoggerFactory.getLogger(PrometheusMetricsQueryClient.class);

    private final MetricsProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public PrometheusMetricsQueryClient(MetricsProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.getQueryTimeout().toMillis());
        factory.setReadTimeout((int) properties.getQueryTimeout().toMillis());

        this.restClient = RestClient.builder()
                .baseUrl(properties.getPrometheusBaseUrl())
                .requestFactory(factory)
                .build();
    }

    public PrometheusMetricsQueryClient(MetricsProperties properties, ObjectMapper objectMapper, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder
                .baseUrl(properties.getPrometheusBaseUrl())
                .build();
    }

    @Override
    public boolean isAvailable() {
        if (!properties.isEnabled()) {
            return false;
        }
        try {
            var response = restClient.get()
                    .uri("/-/healthy")
                    .retrieve()
                    .toBodilessEntity();
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception ex) {
            log.debug("Prometheus is unreachable at {}: {}", properties.getPrometheusBaseUrl(), ex.getMessage());
            return false;
        }
    }

    @Override
    public MetricInstantResult queryInstant(String query, Instant time) {
        if (!properties.isEnabled()) {
            return new MetricInstantResult(query, List.of(), false,
                    List.of("Metrics collection is currently disabled via configuration."));
        }

        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String uri = "/api/v1/query?query=" + encodedQuery;
            if (time != null) {
                uri += "&time=" + time.getEpochSecond();
            }

            String responseBody = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);

            if (responseBody == null || responseBody.isBlank()) {
                return new MetricInstantResult(query, List.of(), false, List.of("Empty response from Prometheus"));
            }

            return parseInstantResult(query, responseBody);

        } catch (Exception ex) {
            log.warn("Prometheus instant query failed for '{}': {}", query, ex.getMessage());
            return new MetricInstantResult(query, List.of(), false,
                    List.of("Prometheus query failed: " + cleanErrorMessage(ex)));
        }
    }

    @Override
    public MetricRangeResult queryRange(String query, Instant start, Instant end, Duration step) {
        if (!properties.isEnabled()) {
            return new MetricRangeResult(query, List.of(), false,
                    List.of("Metrics collection is currently disabled via configuration."));
        }

        try {
            long startSec = start.getEpochSecond();
            long endSec = end.getEpochSecond();
            long stepSec = Math.max(1, step != null ? step.toSeconds() : 15);

            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String uri = String.format("/api/v1/query_range?query=%s&start=%d&end=%d&step=%ds",
                    encodedQuery, startSec, endSec, stepSec);

            String responseBody = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);

            if (responseBody == null || responseBody.isBlank()) {
                return new MetricRangeResult(query, List.of(), false, List.of("Empty response from Prometheus"));
            }

            return parseRangeResult(query, responseBody);

        } catch (Exception ex) {
            log.warn("Prometheus range query failed for '{}': {}", query, ex.getMessage());
            return new MetricRangeResult(query, List.of(), false,
                    List.of("Prometheus query failed: " + cleanErrorMessage(ex)));
        }
    }

    @Override
    public List<PrometheusAlertDto> getAlerts() {
        if (!properties.isEnabled()) {
            return List.of();
        }

        try {
            String responseBody = restClient.get()
                    .uri("/api/v1/alerts")
                    .retrieve()
                    .body(String.class);

            if (responseBody == null || responseBody.isBlank()) {
                return List.of();
            }

            return parseAlerts(responseBody);

        } catch (Exception ex) {
            log.warn("Failed to retrieve alerts from Prometheus: {}", ex.getMessage());
            return List.of();
        }
    }

    @Override
    public String getProviderName() {
        return "prometheus";
    }

    @Override
    public String getEndpointUrl() {
        return properties.getPrometheusBaseUrl();
    }

    private MetricInstantResult parseInstantResult(String query, String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            String status = root.path("status").asText("unknown");
            if (!"success".equalsIgnoreCase(status)) {
                String error = root.path("error").asText("Unknown Prometheus error");
                return new MetricInstantResult(query, List.of(), false, List.of(error));
            }

            JsonNode dataNode = root.path("data");
            String resultType = dataNode.path("resultType").asText("");
            JsonNode resultNode = dataNode.path("result");

            List<MetricSeriesDto> seriesList = new ArrayList<>();

            if (resultNode.isArray() && !resultNode.isEmpty()) {
                for (JsonNode item : resultNode) {
                    Map<String, String> labels = extractLabels(item.path("metric"));
                    JsonNode valueNode = item.path("value");

                    if (valueNode.isArray() && valueNode.size() >= 2) {
                        double ts = valueNode.get(0).asDouble();
                        double val = parseDoubleSafe(valueNode.get(1).asText());
                        Instant instant = Instant.ofEpochMilli((long) (ts * 1000));
                        seriesList.add(new MetricSeriesDto(labels, List.of(new MetricSampleDto(instant, val))));
                    }
                }
            }

            boolean hasData = !seriesList.isEmpty();
            return new MetricInstantResult(query, seriesList, hasData, List.of());

        } catch (Exception ex) {
            log.error("Failed to parse Prometheus instant query response: {}", ex.getMessage(), ex);
            return new MetricInstantResult(query, List.of(), false, List.of("Parse error: " + cleanErrorMessage(ex)));
        }
    }

    private MetricRangeResult parseRangeResult(String query, String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            String status = root.path("status").asText("unknown");
            if (!"success".equalsIgnoreCase(status)) {
                String error = root.path("error").asText("Unknown Prometheus error");
                return new MetricRangeResult(query, List.of(), false, List.of(error));
            }

            JsonNode dataNode = root.path("data");
            JsonNode resultNode = dataNode.path("result");

            List<MetricSeriesDto> seriesList = new ArrayList<>();

            if (resultNode.isArray() && !resultNode.isEmpty()) {
                for (JsonNode item : resultNode) {
                    Map<String, String> labels = extractLabels(item.path("metric"));
                    JsonNode valuesNode = item.path("values");
                    List<MetricSampleDto> samples = new ArrayList<>();

                    if (valuesNode.isArray()) {
                        for (JsonNode sampleNode : valuesNode) {
                            if (sampleNode.isArray() && sampleNode.size() >= 2) {
                                double ts = sampleNode.get(0).asDouble();
                                double val = parseDoubleSafe(sampleNode.get(1).asText());
                                Instant instant = Instant.ofEpochMilli((long) (ts * 1000));
                                samples.add(new MetricSampleDto(instant, val));
                            }
                        }
                    }

                    if (!samples.isEmpty()) {
                        seriesList.add(new MetricSeriesDto(labels, samples));
                    }
                }
            }

            boolean hasData = !seriesList.isEmpty();
            return new MetricRangeResult(query, seriesList, hasData, List.of());

        } catch (Exception ex) {
            log.error("Failed to parse Prometheus range query response: {}", ex.getMessage(), ex);
            return new MetricRangeResult(query, List.of(), false, List.of("Parse error: " + cleanErrorMessage(ex)));
        }
    }

    private List<PrometheusAlertDto> parseAlerts(String json) {
        List<PrometheusAlertDto> alerts = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode alertsNode = root.path("data").path("alerts");

            if (alertsNode.isArray()) {
                for (JsonNode node : alertsNode) {
                    JsonNode labelsNode = node.path("labels");
                    Map<String, String> labels = extractLabels(labelsNode);

                    String name = labels.getOrDefault("alertname", "UnknownAlert");
                    String state = node.path("state").asText("firing").toUpperCase();
                    String severity = labels.getOrDefault("severity", "warning");
                    String app = labels.getOrDefault("application", labels.getOrDefault("job", "unknown"));
                    String env = labels.getOrDefault("environment", "unknown");

                    JsonNode annotationsNode = node.path("annotations");
                    String summary = annotationsNode.path("summary").asText(name);
                    String description = annotationsNode.path("description").asText(summary);

                    String activeAtStr = node.path("activeAt").asText(null);
                    Instant activeAt = Instant.now();
                    if (activeAtStr != null && !activeAtStr.isBlank()) {
                        try {
                            activeAt = Instant.parse(activeAtStr);
                        } catch (Exception ignored) {}
                    }

                    alerts.add(new PrometheusAlertDto(
                            name,
                            state,
                            severity,
                            summary,
                            description,
                            app,
                            env,
                            activeAt,
                            labels
                    ));
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to parse Prometheus alerts response: {}", ex.getMessage());
        }
        return alerts;
    }

    private Map<String, String> extractLabels(JsonNode metricNode) {
        Map<String, String> labels = new LinkedHashMap<>();
        if (metricNode != null && metricNode.isObject()) {
            metricNode.fields().forEachRemaining(entry -> labels.put(entry.getKey(), entry.getValue().asText()));
        }
        return labels;
    }

    private double parseDoubleSafe(String val) {
        if (val == null || val.isBlank()) return 0.0;
        try {
            if ("+Inf".equalsIgnoreCase(val) || "Inf".equalsIgnoreCase(val)) {
                return Double.POSITIVE_INFINITY;
            }
            if ("-Inf".equalsIgnoreCase(val)) {
                return Double.NEGATIVE_INFINITY;
            }
            if ("NaN".equalsIgnoreCase(val)) {
                return Double.NaN;
            }
            return Double.parseDouble(val.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private String cleanErrorMessage(Exception ex) {
        String msg = ex.getMessage();
        return (msg != null && !msg.isBlank()) ? msg : ex.getClass().getSimpleName();
    }
}
