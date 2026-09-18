package com.example.prodsupport.logging.client;

import com.example.prodsupport.logging.config.LoggingProperties;
import com.example.prodsupport.logging.model.*;
import com.example.prodsupport.logging.query.LogQueryBuilder;
import com.example.prodsupport.logging.sanitizer.LogSanitizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.*;

@Component
public class ElasticsearchLogSearchClient implements LogSearchClient {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchLogSearchClient.class);

    private final RestClient restClient;
    private final LogQueryBuilder queryBuilder;
    private final ObjectMapper objectMapper;
    private final LoggingProperties properties;
    private final LogSanitizer sanitizer;

    public ElasticsearchLogSearchClient(RestClient restClient,
                                        LogQueryBuilder queryBuilder,
                                        ObjectMapper objectMapper,
                                        LoggingProperties properties,
                                        LogSanitizer sanitizer) {
        this.restClient = restClient;
        this.queryBuilder = queryBuilder;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.sanitizer = sanitizer;
    }

    @Override
    public LogSearchResult search(LogSearchCriteria criteria) {
        var context = queryBuilder.validateAndBuild(criteria);
        String index = context.indexPattern();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", context.query());
        body.put("size", context.limit());
        body.put("sort", List.of(Map.of("@timestamp", Map.of("order", "desc"))));

        try {
            Request request = new Request("POST", "/" + index + "/_search");
            request.setJsonEntity(objectMapper.writeValueAsString(body));

            Response response = restClient.performRequest(request);
            String responseBody = EntityUtils.toString(response.getEntity());
            JsonNode root = objectMapper.readTree(responseBody);

            long totalHits = 0;
            JsonNode totalNode = root.path("hits").path("total");
            if (totalNode.isObject() && totalNode.has("value")) {
                totalHits = totalNode.path("value").asLong();
            } else if (totalNode.isNumber()) {
                totalHits = totalNode.asLong();
            }

            List<LogEntryDto> logs = new ArrayList<>();
            JsonNode hitsArray = root.path("hits").path("hits");
            if (hitsArray.isArray()) {
                for (JsonNode hit : hitsArray) {
                    JsonNode src = hit.path("_source");
                    logs.add(new LogEntryDto(
                            src.path("@timestamp").asText(null),
                            src.path("level").asText(null),
                            src.path("errorType").asText(null),
                            sanitizer.sanitize(src.path("message").asText("")),
                            src.path("correlationId").asText(null),
                            src.path("component").asText(null),
                            src.path("logger").asText(null),
                            src.path("traceId").asText(null)
                    ));
                }
            }

            boolean truncated = totalHits > context.limit();
            List<String> warnings = new ArrayList<>();
            if (truncated) {
                warnings.add("Results truncated: retrieved " + logs.size() + " of " + totalHits + " matching log events");
            }

            return new LogSearchResult(context.applicationName(), context.environment(), totalHits, truncated, logs, warnings);

        } catch (ResponseException ex) {
            if (ex.getResponse().getStatusLine().getStatusCode() == 404) {
                return new LogSearchResult(context.applicationName(), context.environment(), 0, false, List.of(),
                        List.of("No Elasticsearch index matching '" + index + "' found yet"));
            }
            log.warn("Elasticsearch search query returned HTTP error: {}", ex.getMessage());
            return new LogSearchResult(context.applicationName(), context.environment(), 0, false, List.of(),
                    List.of("Elasticsearch search failed: " + ex.getMessage()));
        } catch (Exception ex) {
            log.warn("Elasticsearch search unavailable or timed out: {}", ex.getMessage());
            return new LogSearchResult(context.applicationName(), context.environment(), 0, false, List.of(),
                    List.of("Centralized log search currently unavailable (" + cleanErrorMessage(ex) + ")"));
        }
    }

    @Override
    public ErrorPatternResult summarizeErrors(LogSearchCriteria criteria) {
        // Enforce ERROR / WARN levels if not explicitly provided
        List<String> levels = criteria.levels();
        if (levels == null || levels.isEmpty()) {
            levels = List.of("ERROR", "WARN");
        }
        LogSearchCriteria errorCriteria = new LogSearchCriteria(
                criteria.applicationName(),
                criteria.environment(),
                criteria.startTime(),
                criteria.endTime(),
                levels,
                criteria.keyword(),
                criteria.correlationId(),
                criteria.limit(),
                criteria.indexPattern()
        );

        var context = queryBuilder.validateAndBuild(errorCriteria);
        String index = context.indexPattern();
        int windowMinutes = (int) Duration.between(context.startTime(), context.endTime()).toMinutes();
        if (windowMinutes <= 0) {
            windowMinutes = properties.getDefaultWindowMinutes();
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", context.query());
        body.put("size", 0);
        body.put("aggs", Map.of(
                "error_types", Map.of(
                        "terms", Map.of(
                                "field", "errorType",
                                "size", Math.min(50, context.limit()),
                                "missing", "UncategorizedError"
                        )
                )
        ));

        try {
            Request request = new Request("POST", "/" + index + "/_search");
            request.setJsonEntity(objectMapper.writeValueAsString(body));

            Response response = restClient.performRequest(request);
            String responseBody = EntityUtils.toString(response.getEntity());
            JsonNode root = objectMapper.readTree(responseBody);

            List<ErrorPatternDto> patterns = new ArrayList<>();
            JsonNode buckets = root.path("aggregations").path("error_types").path("buckets");
            if (buckets.isArray()) {
                for (JsonNode bucket : buckets) {
                    String errorType = bucket.path("key").asText("UncategorizedError");
                    long count = bucket.path("doc_count").asLong(0);
                    patterns.add(new ErrorPatternDto(errorType, count));
                }
            }

            return new ErrorPatternResult(context.applicationName(), context.environment(), windowMinutes, patterns, List.of());

        } catch (ResponseException ex) {
            if (ex.getResponse().getStatusLine().getStatusCode() == 404) {
                return new ErrorPatternResult(context.applicationName(), context.environment(), windowMinutes, List.of(),
                        List.of("No Elasticsearch index matching '" + index + "' found"));
            }
            log.warn("Elasticsearch error aggregation returned HTTP error: {}", ex.getMessage());
            return new ErrorPatternResult(context.applicationName(), context.environment(), windowMinutes, List.of(),
                    List.of("Elasticsearch error aggregation failed: " + ex.getMessage()));
        } catch (Exception ex) {
            log.warn("Elasticsearch aggregation unavailable or timed out: {}", ex.getMessage());
            return new ErrorPatternResult(context.applicationName(), context.environment(), windowMinutes, List.of(),
                    List.of("Centralized error pattern aggregation currently unavailable (" + cleanErrorMessage(ex) + ")"));
        }
    }

    @Override
    public LogTimelineResult getTimeline(LogSearchCriteria criteria) {
        var context = queryBuilder.validateAndBuild(criteria);
        String index = context.indexPattern();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", context.query());
        body.put("size", context.limit());
        body.put("sort", List.of(Map.of("@timestamp", Map.of("order", "asc"))));

        try {
            Request request = new Request("POST", "/" + index + "/_search");
            request.setJsonEntity(objectMapper.writeValueAsString(body));

            Response response = restClient.performRequest(request);
            String responseBody = EntityUtils.toString(response.getEntity());
            JsonNode root = objectMapper.readTree(responseBody);

            List<LogTimelineEventDto> events = new ArrayList<>();
            JsonNode hitsArray = root.path("hits").path("hits");
            if (hitsArray.isArray()) {
                for (JsonNode hit : hitsArray) {
                    JsonNode src = hit.path("_source");
                    events.add(new LogTimelineEventDto(
                            src.path("@timestamp").asText(null),
                            src.path("level").asText(null),
                            src.path("component").asText(null),
                            src.path("errorType").asText(null),
                            sanitizer.sanitize(src.path("message").asText("")),
                            src.path("correlationId").asText(null)
                    ));
                }
            }

            return new LogTimelineResult(context.applicationName(), context.environment(), events, List.of());

        } catch (ResponseException ex) {
            if (ex.getResponse().getStatusLine().getStatusCode() == 404) {
                return new LogTimelineResult(context.applicationName(), context.environment(), List.of(),
                        List.of("No Elasticsearch index matching '" + index + "' found"));
            }
            log.warn("Elasticsearch timeline query returned HTTP error: {}", ex.getMessage());
            return new LogTimelineResult(context.applicationName(), context.environment(), List.of(),
                    List.of("Elasticsearch timeline query failed: " + ex.getMessage()));
        } catch (Exception ex) {
            log.warn("Elasticsearch timeline query unavailable or timed out: {}", ex.getMessage());
            return new LogTimelineResult(context.applicationName(), context.environment(), List.of(),
                    List.of("Centralized log timeline currently unavailable (" + cleanErrorMessage(ex) + ")"));
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            Request request = new Request("GET", "/_cluster/health");
            Response response = restClient.performRequest(request);
            return response.getStatusLine().getStatusCode() == 200;
        } catch (Exception ex) {
            log.debug("Elasticsearch cluster health check failed: {}", ex.getMessage());
            return false;
        }
    }

    private String cleanErrorMessage(Exception ex) {
        String msg = ex.getMessage();
        return msg != null && !msg.isBlank() ? msg : ex.getClass().getSimpleName();
    }
}
