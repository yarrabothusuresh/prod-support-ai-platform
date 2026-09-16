package com.example.prodsupport.database.dto;

import com.example.prodsupport.database.model.DatabaseType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DatabaseConfigRequest(
        @NotNull
        DatabaseType databaseType,

        @NotBlank
        String displayName,

        @NotBlank
        String jdbcUrl,

        @NotBlank
        String username,

        String credentialReference,

        Boolean enabled
) {
}
