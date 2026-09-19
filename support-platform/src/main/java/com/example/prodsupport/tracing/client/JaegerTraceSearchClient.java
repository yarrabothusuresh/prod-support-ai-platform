package com.example.prodsupport.tracing.client;

import com.example.prodsupport.tracing.config.TracingProperties;
import com.example.prodsupport.tracing.dto.SpanDetailDto;
import com.example.prodsupport.tracing.dto.TraceSummaryDto;
import com.example.prodsupport.tracing.model.TraceDetailResult;
import com.example.prodsupport.tracing.model.TraceSearchResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Component
public class JaegerTraceSearchClient implements TraceSearchClient {

    private static final Logger log = LoggerFactory.getLogger(JaegerTraceSearchClient.class);
    private static final Pattern VALID_TRACE_ID_PATTERN = Pattern.compile("^[0-9a-fA-F]{16,32}$");

    private static final Set<String> SENSITIVE_TAG_KEYWORDS = Set.of(
            "password", "secret", "token", "authorization", "auth", "credential",
            "body", "request_body", "payload", "sql.params", "parameter", "card", "cvv"
    );

    private final TracingProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public JaegerTraceSearchClient(TracingProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.getRequestTimeout().toMillis());
        factory.setReadTimeout((int) properties.getRequestTimeout().toMillis());

        this.restClient = RestClient.builder()
                .baseUrl(properties.getJaegerBaseUrl())
                .requestFactory(factory)
                .build();
    }

    @org.springframework.beans.factory.annotation.Autowired
    public JaegerTraceSearchClient(TracingProperties properties, ObjectMapper objectMapper, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder
                .baseUrl(properties.getJaegerBaseUrl())
                .build();
    }

    @Override
    public boolean isAvailable() {
        if (!properties.isEnabled()) {
            return false;
        }
        try {
            var response = restClient.get()
                    .uri("/api/services")
                    .retrieve()
                    .toBodilessEntity();
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception ex) {
            log.debug("Jaeger is not reachable at {}: {}", properties.getJaegerBaseUrl(), ex.getMessage());
            return false;
        }
    }

    @Override
    public TraceSearchResult searchTraces(String serviceName, String environment, int minutes, int limit, boolean errorOnly) {
        if (!properties.isEnabled()) {
            return new TraceSearchResult(serviceName, environment, List.of(),
                    List.of("Distributed tracing is currently disabled via configuration."));
        }

        int effectiveMinutes = Math.min(Math.max(1, minutes), properties.getMaximumWindowMinutes());
        int effectiveLimit = Math.min(Math.max(1, limit), properties.getMaximumResultLimit());

        try {
            String uri = String.format("/api/traces?service=%s&lookback=%dm&limit=%d",
                    serviceName.trim(), effectiveMinutes, effectiveLimit);

            if (errorOnly) {
                uri += "&tags={\"error\":\"true\"}";
            }

            String responseBody = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);

            if (responseBody == null || responseBody.isBlank()) {
                return new TraceSearchResult(serviceName, environment, List.of(), List.of());
            }

            return parseSearchResults(serviceName, environment, responseBody, errorOnly, effectiveLimit);

        } catch (Exception ex) {
            log.warn("Failed to retrieve traces from Jaeger for service '{}': {}", serviceName, ex.getMessage());
            return new TraceSearchResult(serviceName, environment, List.of(),
                    List.of("Distributed tracing (Jaeger) is currently unavailable: " + cleanErrorMessage(ex)));
        }
    }

    @Override
    public Optional<TraceDetailResult> getTraceById(String traceId) {
        if (traceId == null || !VALID_TRACE_ID_PATTERN.matcher(traceId.trim()).matches()) {
            throw new IllegalArgumentException("Invalid traceId format: '" + traceId + "'. Must be 16 to 32 hex characters.");
        }

        String validTraceId = traceId.trim().toLowerCase();

        if (!properties.isEnabled()) {
            return Optional.of(new TraceDetailResult(validTraceId, 0, false, List.of(), Set.of(),
                    List.of("Distributed tracing is currently disabled via configuration.")));
        }

        try {
            String uri = "/api/traces/" + validTraceId;
            String responseBody = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);

            if (responseBody == null || responseBody.isBlank()) {
                return Optional.empty();
            }

            return parseTraceDetail(validTraceId, responseBody);

        } catch (Exception ex) {
            log.warn("Failed to retrieve trace '{}' from Jaeger: {}", validTraceId, ex.getMessage());
            return Optional.of(new TraceDetailResult(validTraceId, 0, false, List.of(), Set.of(),
                    List.of("Distributed tracing (Jaeger) is currently unavailable: " + cleanErrorMessage(ex))));
        }
    }

    @Override
    public String getProviderName() {
        return "jaeger";
    }

    @Override
    public String getEndpointUrl() {
        return properties.getJaegerBaseUrl();
    }

    private TraceSearchResult parseSearchResults(String serviceName, String environment, String json, boolean errorOnly, int limit) {
        List<TraceSummaryDto> traces = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode dataNode = root.get("data");

            if (dataNode != null && dataNode.isArray()) {
                for (JsonNode traceNode : dataNode) {
                    if (traces.size() >= limit) {
                        break;
                    }
                    TraceSummaryDto summary = extractTraceSummary(traceNode);
                    if (summary != null) {
                        if (!errorOnly || summary.hasError()) {
                            traces.add(summary);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.error("Failed to parse Jaeger search response: {}", ex.getMessage(), ex);
            warnings.add("Failed to parse trace response: " + cleanErrorMessage(ex));
        }

        return new TraceSearchResult(serviceName, environment, traces, warnings);
    }

    private TraceSummaryDto extractTraceSummary(JsonNode traceNode) {
        String traceId = traceNode.path("traceID").asText(null);
        if (traceId == null || traceId.isBlank()) {
            return null;
        }

        JsonNode spansNode = traceNode.path("spans");
        if (!spansNode.isArray() || spansNode.isEmpty()) {
            return null;
        }

        int spanCount = spansNode.size();
        long minStartTimeUs = Long.MAX_VALUE;
        long maxEndTimeUs = Long.MIN_VALUE;
        boolean hasError = false;
        String rootService = "unknown";
        String rootOperation = "unknown";

        Map<String, String> processServiceMap = new HashMap<>();
        JsonNode processesNode = traceNode.path("processes");
        if (processesNode.isObject()) {
            processesNode.fields().forEachRemaining(entry -> {
                String procId = entry.getKey();
                String svcName = entry.getValue().path("serviceName").asText("unknown");
                processServiceMap.put(procId, svcName);
            });
        }

        for (JsonNode span : spansNode) {
            long startTimeUs = span.path("startTime").asLong(0);
            long durationUs = span.path("duration").asLong(0);
            long endTimeUs = startTimeUs + durationUs;

            if (startTimeUs < minStartTimeUs) {
                minStartTimeUs = startTimeUs;
                String procId = span.path("processID").asText("");
                rootService = processServiceMap.getOrDefault(procId, "unknown");
                rootOperation = span.path("operationName").asText("unknown");
            }
            if (endTimeUs > maxEndTimeUs) {
                maxEndTimeUs = endTimeUs;
            }

            JsonNode tags = span.path("tags");
            if (tags.isArray()) {
                for (JsonNode tag : tags) {
                    if ("error".equalsIgnoreCase(tag.path("key").asText())) {
                        if (tag.path("value").asBoolean(false) || "true".equalsIgnoreCase(tag.path("value").asText())) {
                            hasError = true;
                        }
                    }
                }
            }
        }

        long durationMs = (maxEndTimeUs > minStartTimeUs && minStartTimeUs != Long.MAX_VALUE)
                ? (maxEndTimeUs - minStartTimeUs) / 1000 : 0;

        String startedAt = (minStartTimeUs != Long.MAX_VALUE)
                ? Instant.ofEpochMilli(minStartTimeUs / 1000).toString()
                : Instant.now().toString();

        return new TraceSummaryDto(traceId, rootService, rootOperation, durationMs, hasError, spanCount, startedAt);
    }

    private Optional<TraceDetailResult> parseTraceDetail(String traceId, String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode dataNode = root.get("data");

            if (dataNode == null || !dataNode.isArray() || dataNode.isEmpty()) {
                return Optional.empty();
            }

            JsonNode traceNode = dataNode.get(0);
            JsonNode spansNode = traceNode.path("spans");

            if (!spansNode.isArray() || spansNode.isEmpty()) {
                return Optional.empty();
            }

            Map<String, String> processServiceMap = new HashMap<>();
            JsonNode processesNode = traceNode.path("processes");
            if (processesNode.isObject()) {
                processesNode.fields().forEachRemaining(entry -> {
                    String procId = entry.getKey();
                    String svcName = entry.getValue().path("serviceName").asText("unknown");
                    processServiceMap.put(procId, svcName);
                });
            }

            long minStartTimeUs = Long.MAX_VALUE;
            long maxEndTimeUs = Long.MIN_VALUE;
            boolean hasError = false;
            Set<String> participatingServices = new LinkedHashSet<>();

            for (JsonNode span : spansNode) {
                long st = span.path("startTime").asLong(0);
                long dur = span.path("duration").asLong(0);
                if (st < minStartTimeUs) minStartTimeUs = st;
                if (st + dur > maxEndTimeUs) maxEndTimeUs = st + dur;

                String procId = span.path("processID").asText("");
                String svc = processServiceMap.getOrDefault(procId, "unknown");
                participatingServices.add(svc);
            }

            long totalDurationMs = (maxEndTimeUs > minStartTimeUs && minStartTimeUs != Long.MAX_VALUE)
                    ? (maxEndTimeUs - minStartTimeUs) / 1000 : 0;

            List<SpanDetailDto> spanList = new ArrayList<>();

            for (JsonNode span : spansNode) {
                String spanId = span.path("spanID").asText("");
                String operation = span.path("operationName").asText("unknown");
                long startTimeUs = span.path("startTime").asLong(0);
                long durationUs = span.path("duration").asLong(0);
                long durationMs = durationUs / 1000;
                long startOffsetMs = (startTimeUs >= minStartTimeUs && minStartTimeUs != Long.MAX_VALUE)
                        ? (startTimeUs - minStartTimeUs) / 1000 : 0;

                String procId = span.path("processID").asText("");
                String service = processServiceMap.getOrDefault(procId, "unknown");

                String parentSpanId = null;
                JsonNode refs = span.path("references");
                if (refs.isArray()) {
                    for (JsonNode ref : refs) {
                        if ("CHILD_OF".equalsIgnoreCase(ref.path("refType").asText())) {
                            parentSpanId = ref.path("spanID").asText(null);
                            break;
                        }
                    }
                }

                Map<String, String> sanitizedTags = new LinkedHashMap<>();
                boolean spanError = false;
                String errorCategory = null;
                String sanitizedErrorDesc = null;

                JsonNode tags = span.path("tags");
                if (tags.isArray()) {
                    for (JsonNode tag : tags) {
                        String key = tag.path("key").asText("");
                        String val = tag.path("value").asText("");

                        if ("error".equalsIgnoreCase(key)) {
                            if (tag.path("value").asBoolean(false) || "true".equalsIgnoreCase(val)) {
                                spanError = true;
                                hasError = true;
                            }
                        } else if ("error.type".equalsIgnoreCase(key) || "exception.type".equalsIgnoreCase(key)) {
                            errorCategory = sanitizeText(val);
                        } else if ("error.message".equalsIgnoreCase(key) || "exception.message".equalsIgnoreCase(key)) {
                            sanitizedErrorDesc = sanitizeText(val);
                        } else if (!isSensitiveKey(key)) {
                            sanitizedTags.put(key, sanitizeText(val));
                        }
                    }
                }

                String status = spanError ? "ERROR" : "OK";

                spanList.add(new SpanDetailDto(
                        spanId,
                        parentSpanId,
                        service,
                        operation,
                        durationMs,
                        startOffsetMs,
                        status,
                        errorCategory,
                        sanitizedErrorDesc,
                        sanitizedTags
                ));
            }

            spanList.sort(Comparator.comparingLong(SpanDetailDto::startOffsetMs));

            return Optional.of(new TraceDetailResult(
                    traceId,
                    totalDurationMs,
                    hasError,
                    spanList,
                    participatingServices,
                    List.of()
            ));

        } catch (Exception ex) {
            log.error("Failed to parse Jaeger trace detail for '{}': {}", traceId, ex.getMessage(), ex);
            return Optional.of(new TraceDetailResult(
                    traceId,
                    0,
                    false,
                    List.of(),
                    Set.of(),
                    List.of("Error parsing trace detail: " + cleanErrorMessage(ex))
            ));
        }
    }

    private boolean isSensitiveKey(String key) {
        if (key == null) return false;
        String lower = key.toLowerCase();
        for (String s : SENSITIVE_TAG_KEYWORDS) {
            if (lower.contains(s)) {
                return true;
            }
        }
        return false;
    }

    private String sanitizeText(String text) {
        if (text == null) return null;
        if (text.length() > 200) {
            return text.substring(0, 200) + "...";
        }
        return text;
    }

    private String cleanErrorMessage(Exception ex) {
        String msg = ex.getMessage();
        return (msg != null && !msg.isBlank()) ? msg : ex.getClass().getSimpleName();
    }
}
