package com.example.prodsupport.logging;

import com.example.prodsupport.logging.config.LoggingProperties;
import com.example.prodsupport.logging.model.LogSearchCriteria;
import com.example.prodsupport.logging.query.LogQueryBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LogQueryBuilderTest {

    private LogQueryBuilder queryBuilder;
    private LoggingProperties properties;

    @BeforeEach
    void setUp() {
        properties = new LoggingProperties();
        properties.setDefaultWindowMinutes(15);
        properties.setMaximumWindowHours(24);
        properties.setDefaultResultLimit(20);
        properties.setMaximumResultLimit(100);
        properties.setDefaultIndexPattern("prod-support-logs-*");
        queryBuilder = new LogQueryBuilder(properties);
    }

    @Test
    @DisplayName("Should enforce mandatory applicationName, environment, and timestamp filters")
    void testMandatoryFiltersAlwaysIncluded() {
        Instant now = Instant.now();
        Instant start = now.minus(Duration.ofMinutes(10));

        LogSearchCriteria criteria = new LogSearchCriteria(
                "payment-service",
                "local",
                start,
                now,
                List.of("ERROR"),
                "timeout",
                "CORR-123",
                25,
                null
        );

        var context = queryBuilder.validateAndBuild(criteria);

        assertThat(context.applicationName()).isEqualTo("payment-service");
        assertThat(context.environment()).isEqualTo("local");
        assertThat(context.limit()).isEqualTo(25);
        assertThat(context.indexPattern()).isEqualTo("prod-support-logs-*");

        @SuppressWarnings("unchecked")
        Map<String, Object> boolMap = (Map<String, Object>) context.query().get("bool");
        assertThat(boolMap).isNotNull();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> filters = (List<Map<String, Object>>) boolMap.get("filter");
        assertThat(filters).isNotEmpty();

        // Check app name filter
        assertThat(filters).anyMatch(f -> f.containsKey("term") && ((Map<?, ?>) f.get("term")).containsKey("applicationName"));
        // Check env filter
        assertThat(filters).anyMatch(f -> f.containsKey("term") && ((Map<?, ?>) f.get("term")).containsKey("environment"));
        // Check time range filter
        assertThat(filters).anyMatch(f -> f.containsKey("range") && ((Map<?, ?>) f.get("range")).containsKey("@timestamp"));
    }

    @Test
    @DisplayName("Should reject blank or missing applicationName")
    void testRejectBlankApplicationName() {
        LogSearchCriteria criteria = new LogSearchCriteria(
                "   ",
                "local",
                null, null, List.of(), null, null, null, null
        );
        assertThatThrownBy(() -> queryBuilder.validateAndBuild(criteria))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("applicationName");
    }

    @Test
    @DisplayName("Should reject blank or missing environment")
    void testRejectBlankEnvironment() {
        LogSearchCriteria criteria = new LogSearchCriteria(
                "payment-service",
                null,
                null, null, List.of(), null, null, null, null
        );
        assertThatThrownBy(() -> queryBuilder.validateAndBuild(criteria))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("environment");
    }

    @Test
    @DisplayName("Should reject invalid time range where start is after end")
    void testRejectStartAfterEnd() {
        Instant now = Instant.now();
        LogSearchCriteria criteria = new LogSearchCriteria(
                "payment-service",
                "local",
                now,
                now.minus(Duration.ofMinutes(10)),
                List.of(), null, null, null, null
        );
        assertThatThrownBy(() -> queryBuilder.validateAndBuild(criteria))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("startTime");
    }

    @Test
    @DisplayName("Should reject time range exceeding maximum allowed window (24h)")
    void testRejectExcessiveWindow() {
        Instant now = Instant.now();
        LogSearchCriteria criteria = new LogSearchCriteria(
                "payment-service",
                "local",
                now.minus(Duration.ofHours(30)),
                now,
                List.of(), null, null, null, null
        );
        assertThatThrownBy(() -> queryBuilder.validateAndBuild(criteria))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds maximum allowed limit");
    }

    @Test
    @DisplayName("Should reject future end time beyond allowable clock skew")
    void testRejectFutureEndTime() {
        Instant future = Instant.now().plus(Duration.ofHours(2));
        LogSearchCriteria criteria = new LogSearchCriteria(
                "payment-service",
                "local",
                Instant.now(),
                future,
                List.of(), null, null, null, null
        );
        assertThatThrownBy(() -> queryBuilder.validateAndBuild(criteria))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("clock skew");
    }

    @Test
    @DisplayName("Should reject invalid log levels")
    void testRejectInvalidLogLevel() {
        LogSearchCriteria criteria = new LogSearchCriteria(
                "payment-service",
                "local",
                null, null,
                List.of("INVALID_LEVEL"),
                null, null, null, null
        );
        assertThatThrownBy(() -> queryBuilder.validateAndBuild(criteria))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid log level");
    }

    @Test
    @DisplayName("Should enforce maximum result limit clamp")
    void testClampResultLimit() {
        LogSearchCriteria criteria = new LogSearchCriteria(
                "payment-service",
                "local",
                null, null, List.of(), null, null,
                9999, // excessive limit
                null
        );
        var context = queryBuilder.validateAndBuild(criteria);
        assertThat(context.limit()).isEqualTo(properties.getMaximumResultLimit());
    }

    @Test
    @DisplayName("Should reject arbitrary or path traversal index patterns")
    void testRejectArbitraryIndexPattern() {
        LogSearchCriteria criteria = new LogSearchCriteria(
                "payment-service",
                "local",
                null, null, List.of(), null, null, null,
                "arbitrary-customer-index-*"
        );
        assertThatThrownBy(() -> queryBuilder.validateAndBuild(criteria))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unauthorized index pattern");
    }
}
