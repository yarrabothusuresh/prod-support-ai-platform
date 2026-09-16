package com.example.prodsupport.database.provider;

import com.example.prodsupport.database.model.DatabaseActivityResult;
import com.example.prodsupport.database.model.DatabaseHealthResult;
import com.example.prodsupport.database.model.DatabaseType;

public interface DatabaseDiagnosticProvider {

    DatabaseType databaseType();

    DatabaseHealthResult checkHealth(DatabaseDiagnosticContext context);

    DatabaseActivityResult checkActivity(DatabaseDiagnosticContext context);
}
