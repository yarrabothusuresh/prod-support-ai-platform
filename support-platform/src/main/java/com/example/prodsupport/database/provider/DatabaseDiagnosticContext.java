package com.example.prodsupport.database.provider;

import com.example.prodsupport.database.model.DatabaseType;

import javax.sql.DataSource;

public record DatabaseDiagnosticContext(
        String applicationName,
        String environment,
        String databaseName,
        DatabaseType databaseType,
        DataSource dataSource,
        long connectionTimeoutMs,
        long queryTimeoutSeconds
) {
}
