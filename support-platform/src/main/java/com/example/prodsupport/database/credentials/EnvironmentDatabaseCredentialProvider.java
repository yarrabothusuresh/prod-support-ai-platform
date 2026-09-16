package com.example.prodsupport.database.credentials;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class EnvironmentDatabaseCredentialProvider implements DatabaseCredentialProvider {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentDatabaseCredentialProvider.class);

    private final Environment environment;

    public EnvironmentDatabaseCredentialProvider(Environment environment) {
        this.environment = environment;
    }

    @Override
    public String resolvePassword(String credentialReference) {
        if (credentialReference == null || credentialReference.isBlank()) {
            return "";
        }

        String normalized = credentialReference.trim().toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9_]", "_");

        // Candidates:
        // 1. Exact reference (e.g. PAYMENT_DB_PASSWORD)
        // 2. DB_{REF}_PASSWORD
        // 3. {REF}_PASSWORD
        // 4. Direct value from Spring Environment / System property
        String[] keys = new String[]{
                normalized,
                "DB_" + normalized + "_PASSWORD",
                normalized + "_PASSWORD",
                "DB_" + normalized,
                credentialReference
        };

        for (String key : keys) {
            String val = System.getenv(key);
            if (val != null && !val.isBlank()) {
                log.debug("Resolved database credential using environment variable pattern [redacted]");
                return val;
            }
            val = environment.getProperty(key);
            if (val != null && !val.isBlank()) {
                log.debug("Resolved database credential using property [redacted]");
                return val;
            }
        }

        // Fallback default for local dev/demo environment if credentialReference matches PAYMENT_DB
        if ("PAYMENT_DB".equalsIgnoreCase(credentialReference) || "POSTGRES".equalsIgnoreCase(credentialReference)) {
            String defaultSecret = environment.getProperty("spring.datasource.password", "postgres");
            return defaultSecret;
        }

        return "";
    }
}
