package com.example.prodsupport.logging.query;

import com.example.prodsupport.logging.config.LoggingProperties;
import com.example.prodsupport.logging.model.LogSearchCriteria;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Component
public class LogQueryBuilder {

    private static final Set<String> ALLOWED_LEVELS = Set.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL");
    private static final Duration ALLOWED_CLOCK_SKEW = Duration.ofMinutes(5);

    private final LoggingProperties properties;

    public LogQueryBuilder(LoggingProperties properties) {
        this.properties = properties;
    }

    public ValidatedQueryContext validateAndBuild(LogSearchCriteria criteria) {
        if (criteria == null) {
            throw new IllegalArgumentException("LogSearchCriteria must not be null");
        }

        String appName = criteria.applicationName();
        if (appName == null || appName.isBlank()) {
            throw new IllegalArgumentException("applicationName is mandatory and cannot be blank");
        }

        String env = criteria.environment();
        if (env == null || env.isBlank()) {
            throw new IllegalArgumentException("environment is mandatory and cannot be blank");
        }

        Instant now = Instant.now();
        Instant start = criteria.startTime();
        Instant end = criteria.endTime();

        if (end == null) {
            end = now;
        }
        if (start == null) {
            start = end.minus(Duration.ofMinutes(properties.getDefaultWindowMinutes()));
        }

        if (start.isAfter(end)) {
            throw new IllegalArgumentException("startTime (" + start + ") must not be after endTime (" + end + ")");
        }

        if (end.isAfter(now.plus(ALLOWED_CLOCK_SKEW))) {
            throw new IllegalArgumentException("endTime (" + end + ") is too far in the future beyond allowable clock skew");
        }

        long windowHours = Duration.between(start, end).toHours();
        if (windowHours > properties.getMaximumWindowHours()) {
            throw new IllegalArgumentException("Requested time window (" + windowHours + "h) exceeds maximum allowed limit of "
                    + properties.getMaximumWindowHours() + "h");
        }

        List<String> validatedLevels = new ArrayList<>();
        if (criteria.levels() != null && !criteria.levels().isEmpty()) {
            for (String lvl : criteria.levels()) {
                if (lvl == null || !ALLOWED_LEVELS.contains(lvl.trim().toUpperCase())) {
                    throw new IllegalArgumentException("Invalid log level: '" + lvl + "'. Allowed: " + ALLOWED_LEVELS);
                }
                validatedLevels.add(lvl.trim().toUpperCase());
            }
        }

        int limit = criteria.limit() != null && criteria.limit() > 0 ? criteria.limit() : properties.getDefaultResultLimit();
        if (limit > properties.getMaximumResultLimit()) {
            limit = properties.getMaximumResultLimit();
        }

        String indexPattern = criteria.indexPattern();
        if (indexPattern == null || indexPattern.isBlank()) {
            indexPattern = properties.getDefaultIndexPattern();
        } else {
            validateIndexPattern(indexPattern);
        }

        // Build strongly typed JSON query payload structure
        Map<String, Object> boolQuery = new LinkedHashMap<>();
        List<Map<String, Object>> filterList = new ArrayList<>();
        List<Map<String, Object>> mustList = new ArrayList<>();

        // Mandatory applicationName term filter
        filterList.add(Map.of("term", Map.of("applicationName", appName.trim())));

        // Mandatory environment term filter
        filterList.add(Map.of("term", Map.of("environment", env.trim())));

        // Mandatory timestamp range filter
        Map<String, Object> rangeMap = new LinkedHashMap<>();
        rangeMap.put("gte", start.toString());
        rangeMap.put("lte", end.toString());
        filterList.add(Map.of("range", Map.of("@timestamp", rangeMap)));

        // Optional log level filter
        if (!validatedLevels.isEmpty()) {
            if (validatedLevels.size() == 1) {
                filterList.add(Map.of("term", Map.of("level", validatedLevels.getFirst())));
            } else {
                filterList.add(Map.of("terms", Map.of("level", validatedLevels)));
            }
        }

        // Optional correlationId filter
        if (criteria.correlationId() != null && !criteria.correlationId().isBlank()) {
            filterList.add(Map.of("term", Map.of("correlationId", criteria.correlationId().trim())));
        }

        // Optional safe keyword text match
        if (criteria.keyword() != null && !criteria.keyword().isBlank()) {
            mustList.add(Map.of("match", Map.of("message", Map.of("query", criteria.keyword().trim()))));
        }

        boolQuery.put("filter", filterList);
        if (!mustList.isEmpty()) {
            boolQuery.put("must", mustList);
        }

        Map<String, Object> queryRoot = Map.of("bool", boolQuery);

        return new ValidatedQueryContext(appName.trim(), env.trim(), start, end, limit, indexPattern, queryRoot);
    }

    private void validateIndexPattern(String indexPattern) {
        String trimmed = indexPattern.trim();
        if (!trimmed.startsWith("prod-support-logs") || trimmed.contains("..") || trimmed.contains("/") || trimmed.contains("\\")) {
            throw new IllegalArgumentException("Arbitrary or unauthorized index pattern is rejected: '" + indexPattern + "'");
        }
    }

    public record ValidatedQueryContext(
            String applicationName,
            String environment,
            Instant startTime,
            Instant endTime,
            int limit,
            String indexPattern,
            Map<String, Object> query
    ) {}
}
