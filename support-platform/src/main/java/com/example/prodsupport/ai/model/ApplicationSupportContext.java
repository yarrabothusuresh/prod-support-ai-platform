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
        List<String> warnings,
        List<SupportErrorDto> recentErrors,
        List<SupportDependencyDto> dependencies,
        boolean errorsAvailable,
        boolean dependenciesAvailable
) {
    public ApplicationSupportContext {
        warnings = warnings == null ? Collections.emptyList() : List.copyOf(warnings);
        recentErrors = recentErrors == null ? Collections.emptyList() : List.copyOf(recentErrors);
        dependencies = dependencies == null ? Collections.emptyList() : List.copyOf(dependencies);
    }

    public ApplicationSupportContext(
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
        this(
                applicationName,
                team,
                environment,
                description,
                baseUrl,
                supportStatus,
                actuatorStatus,
                supportInfoAvailable,
                healthAvailable,
                collectionTimestamp,
                warnings,
                Collections.emptyList(),
                Collections.emptyList(),
                false,
                false
        );
    }
}
