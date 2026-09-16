package com.example.prodsupport.database.provider;

import com.example.prodsupport.database.model.DatabaseActivityResult;
import com.example.prodsupport.database.model.DatabaseHealthResult;
import com.example.prodsupport.database.model.DatabaseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class PostgreSqlDiagnosticProvider implements DatabaseDiagnosticProvider {

    private static final Logger log = LoggerFactory.getLogger(PostgreSqlDiagnosticProvider.class);

    // Strictly pre-defined, read-only diagnostic queries
    private static final String HEALTH_CHECK_SQL = "SELECT 1";

    // Safe aggregate query on pg_stat_activity:
    // Excludes background processes and our own query, aggregates states and waits
    private static final String ACTIVITY_AGGREGATE_SQL =
            "SELECT " +
            "  COUNT(*) FILTER (WHERE state = 'active' AND query NOT ILIKE '%pg_stat_activity%') AS active_count, " +
            "  COUNT(*) FILTER (WHERE state = 'idle') AS idle_count, " +
            "  COUNT(*) FILTER (WHERE state = 'idle in transaction') AS idle_in_trans_count, " +
            "  COUNT(*) FILTER (WHERE wait_event_type IS NOT NULL AND state = 'active' AND query NOT ILIKE '%pg_stat_activity%') AS waiting_count " +
            "FROM pg_stat_activity " +
            "WHERE backend_type = 'client backend'";

    // Safe query for long running queries without exposing query text or parameters
    private static final String LONG_RUNNING_SQL =
            "SELECT " +
            "  COUNT(*) AS long_count, " +
            "  COALESCE(EXTRACT(EPOCH FROM MAX(now() - query_start)) * 1000, 0) AS oldest_duration_ms " +
            "FROM pg_stat_activity " +
            "WHERE state = 'active' " +
            "  AND query NOT ILIKE '%pg_stat_activity%' " +
            "  AND now() - query_start > (? * interval '1 second')";

    @Override
    public DatabaseType databaseType() {
        return DatabaseType.POSTGRESQL;
    }

    @Override
    public DatabaseHealthResult checkHealth(DatabaseDiagnosticContext context) {
        String dbName = context.databaseName() != null ? context.databaseName() : "postgresql";
        Instant start = Instant.now();

        if (context.dataSource() == null) {
            return DatabaseHealthResult.down(dbName, databaseType(), "No DataSource available for health check", 0);
        }

        try (Connection conn = context.dataSource().getConnection()) {
            conn.setReadOnly(true);
            try (PreparedStatement stmt = conn.prepareStatement(HEALTH_CHECK_SQL)) {
                stmt.setQueryTimeout((int) Math.max(1, context.queryTimeoutSeconds()));
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        long duration = Duration.between(start, Instant.now()).toMillis();
                        return DatabaseHealthResult.up(dbName, databaseType(), duration);
                    }
                }
            }
            long duration = Duration.between(start, Instant.now()).toMillis();
            return DatabaseHealthResult.down(dbName, databaseType(), "Health check query returned no rows", duration);
        } catch (Exception ex) {
            long duration = Duration.between(start, Instant.now()).toMillis();
            String msg = sanitizeErrorMessage(ex.getMessage());
            log.warn("PostgreSQL health check failed for {}: {}", dbName, msg);
            return DatabaseHealthResult.down(dbName, databaseType(), msg, duration);
        }
    }

    @Override
    public DatabaseActivityResult checkActivity(DatabaseDiagnosticContext context) {
        List<String> warnings = new ArrayList<>();
        if (context.dataSource() == null) {
            return DatabaseActivityResult.unavailable("No DataSource available for activity check");
        }

        int active = 0;
        int idle = 0;
        int idleInTrans = 0;
        int waiting = 0;
        int longRunningCount = 0;
        long oldestDurationMs = 0;

        try (Connection conn = context.dataSource().getConnection()) {
            conn.setReadOnly(true);

            // 1. Session aggregates
            try (PreparedStatement stmt = conn.prepareStatement(ACTIVITY_AGGREGATE_SQL)) {
                stmt.setQueryTimeout((int) Math.max(1, context.queryTimeoutSeconds()));
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        active = rs.getInt("active_count");
                        idle = rs.getInt("idle_count");
                        idleInTrans = rs.getInt("idle_in_trans_count");
                        waiting = rs.getInt("waiting_count");
                    }
                }
            } catch (Exception ex) {
                String sanitized = sanitizeErrorMessage(ex.getMessage());
                log.warn("Could not retrieve pg_stat_activity session aggregates: {}", sanitized);
                warnings.add("Session activity query restricted or unavailable: " + sanitized);
            }

            // 2. Long running queries check (threshold default: 5s)
            try (PreparedStatement stmt = conn.prepareStatement(LONG_RUNNING_SQL)) {
                stmt.setQueryTimeout((int) Math.max(1, context.queryTimeoutSeconds()));
                stmt.setDouble(1, 5.0); // 5 seconds threshold
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        longRunningCount = rs.getInt("long_count");
                        oldestDurationMs = Math.round(rs.getDouble("oldest_duration_ms"));
                    }
                }
            } catch (Exception ex) {
                String sanitized = sanitizeErrorMessage(ex.getMessage());
                log.warn("Could not retrieve pg_stat_activity long-running queries: {}", sanitized);
                warnings.add("Long-running query check restricted or unavailable: " + sanitized);
            }

            return new DatabaseActivityResult(
                    active,
                    idle,
                    idleInTrans,
                    waiting,
                    longRunningCount,
                    oldestDurationMs,
                    "pg_stat_activity",
                    warnings,
                    Instant.now()
            );

        } catch (Exception ex) {
            String sanitized = sanitizeErrorMessage(ex.getMessage());
            log.warn("Database activity check connection failed: {}", sanitized);
            return DatabaseActivityResult.unavailable("Database activity check failed: " + sanitized);
        }
    }

    private String sanitizeErrorMessage(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Database connection or query error";
        }
        // Scrub potential credentials or IPs if present
        return raw.replaceAll("password=[^\\s&;]+", "password=***")
                .replaceAll("(?i)(user=)[^\\s&;]+", "$1***");
    }
}
