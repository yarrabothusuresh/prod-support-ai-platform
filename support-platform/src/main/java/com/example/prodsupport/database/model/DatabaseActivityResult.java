package com.example.prodsupport.database.model;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

public record DatabaseActivityResult(
        int activeSessions,
        int idleSessions,
        int idleInTransactionSessions,
        int waitingSessions,
        int longRunningQueryCount,
        long oldestDurationMs,
        String source,
        List<String> warnings,
        Instant timestamp
) {
    public static DatabaseActivityResult unavailable(String reason) {
        return new DatabaseActivityResult(
                0,
                0,
                0,
                0,
                0,
                0,
                "pg_stat_activity",
                List.of(reason),
                Instant.now()
        );
    }

    public static DatabaseActivityResult empty() {
        return new DatabaseActivityResult(
                0,
                0,
                0,
                0,
                0,
                0,
                "pg_stat_activity",
                Collections.emptyList(),
                Instant.now()
        );
    }
}
