package com.example.prodsupport.database.model;

import java.util.Collections;
import java.util.List;

public record ApplicationDatabaseDiagnostics(
        boolean enabled,
        String databaseName,
        DatabaseType databaseType,
        DatabaseHealthResult health,
        ConnectionPoolResult connectionPool,
        DatabaseActivityResult activity,
        List<String> warnings
) {
    public static ApplicationDatabaseDiagnostics disabled(String appName) {
        return new ApplicationDatabaseDiagnostics(
                false,
                appName,
                DatabaseType.OTHER,
                DatabaseHealthResult.unknown(appName, DatabaseType.OTHER, "Database diagnostics not enabled"),
                ConnectionPoolResult.unavailable("Database diagnostics not enabled"),
                DatabaseActivityResult.unavailable("Database diagnostics not enabled"),
                Collections.emptyList()
        );
    }
}
