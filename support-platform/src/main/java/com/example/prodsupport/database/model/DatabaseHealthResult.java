package com.example.prodsupport.database.model;

import java.time.Instant;

public record DatabaseHealthResult(
        String databaseName,
        DatabaseType databaseType,
        boolean reachable,
        long responseTimeMs,
        String status, // UP, DEGRADED, DOWN, UNKNOWN
        String message,
        Instant timestamp
) {
    public static DatabaseHealthResult down(String dbName, DatabaseType type, String message, long responseTimeMs) {
        return new DatabaseHealthResult(dbName, type, false, responseTimeMs, "DOWN", message, Instant.now());
    }

    public static DatabaseHealthResult up(String dbName, DatabaseType type, long responseTimeMs) {
        return new DatabaseHealthResult(dbName, type, true, responseTimeMs, "UP", "Database is reachable and responding", Instant.now());
    }

    public static DatabaseHealthResult unknown(String dbName, DatabaseType type, String message) {
        return new DatabaseHealthResult(dbName, type, false, 0, "UNKNOWN", message, Instant.now());
    }
}
