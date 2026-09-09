package com.example.prodsupport.ai.model;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

public record ApplicationSupportContext(
        String applicationName,
        String team,
        String environment,
        String description,
        String baseUrl,
        String supportStatus,
        String actuatorStatus,
        boolean supportInfoAvailable,
        boolean healthAvailable,
        OffsetDateTime collectionTimestamp,
        List<String> warnings
) {
    public ApplicationSupportContext {
        if (warnings == null) {
            warnings = Collections.emptyList();
        } else {
            warnings = List.copyOf(warnings);
        }
    }
}
