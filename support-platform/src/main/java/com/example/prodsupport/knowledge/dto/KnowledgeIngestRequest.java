package com.example.prodsupport.knowledge.dto;

import com.example.prodsupport.domain.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record KnowledgeIngestRequest(
        @NotBlank(message = "applicationName is required")
        String applicationName,

        @NotBlank(message = "environment is required")
        String environment,

        @NotNull(message = "documentType is required")
        DocumentType documentType,

        @NotBlank(message = "title is required")
        String title,

        @NotBlank(message = "source is required")
        String source,

        String version,
        String owner
) {}
