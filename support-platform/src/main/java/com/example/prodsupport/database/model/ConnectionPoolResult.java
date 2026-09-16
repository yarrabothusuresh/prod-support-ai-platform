package com.example.prodsupport.database.model;

import java.time.Instant;

public record ConnectionPoolResult(
        String poolName,
        int activeConnections,
        int idleConnections,
        int totalConnections,
        int maxPoolSize,
        int minimumIdle,
        int threadsAwaitingConnection,
        int utilizationPercent,
        String status, // NORMAL, WARNING, CRITICAL, UNKNOWN
        String message,
        String source,
        Instant timestamp
) {
    public static ConnectionPoolResult unavailable(String reason) {
        return new ConnectionPoolResult(
                "none",
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                "UNKNOWN",
                reason,
                "application-endpoint",
                Instant.now()
        );
    }
}
