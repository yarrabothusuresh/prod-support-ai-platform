package com.example.prodsupport.database.provider;

import com.example.prodsupport.database.model.DatabaseActivityResult;
import com.example.prodsupport.database.model.DatabaseHealthResult;
import com.example.prodsupport.database.model.DatabaseType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OracleDiagnosticProvider implements DatabaseDiagnosticProvider {

    @Override
    public DatabaseType databaseType() {
        return DatabaseType.ORACLE;
    }

    @Override
    public DatabaseHealthResult checkHealth(DatabaseDiagnosticContext context) {
        String dbName = context.databaseName() != null ? context.databaseName() : "oracle";
        return DatabaseHealthResult.unknown(dbName, databaseType(),
                "Oracle diagnostic provider is architecture-ready but not implemented in Day 7");
    }

    @Override
    public DatabaseActivityResult checkActivity(DatabaseDiagnosticContext context) {
        return new DatabaseActivityResult(
                0,
                0,
                0,
                0,
                0,
                0,
                "v$session",
                List.of("Oracle activity diagnostics not implemented in Day 7"),
                java.time.Instant.now()
        );
    }
}
