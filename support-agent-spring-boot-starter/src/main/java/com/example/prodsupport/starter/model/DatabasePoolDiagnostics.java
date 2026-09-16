package com.example.prodsupport.starter.model;

import java.time.Instant;

public record DatabasePoolDiagnostics(
        String poolName,
        int activeConnections,
        int idleConnections,
        int totalConnections,
        int maxPoolSize,
        int minimumIdle,
        int threadsAwaitingConnection,
        int utilizationPercent,
        String status,
        String message,
        Instant timestamp
) {
    public static DatabasePoolDiagnostics unavailable(String reason) {
        return new DatabasePoolDiagnostics(
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
                Instant.now()
        );
    }
}
