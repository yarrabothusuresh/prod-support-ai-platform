package com.example.prodsupport.database.dto;

import com.example.prodsupport.database.model.DatabaseType;

import java.time.Instant;

public record DatabaseConfigDto(
        Long id,
        Long applicationId,
        DatabaseType databaseType,
        String displayName,
        String jdbcUrl,
        String username,
        String credentialReference,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
