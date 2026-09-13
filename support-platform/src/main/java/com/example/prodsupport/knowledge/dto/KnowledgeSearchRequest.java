package com.example.prodsupport.knowledge.dto;

import com.example.prodsupport.domain.DocumentType;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record KnowledgeSearchRequest(
        @NotBlank(message = "applicationName is required")
        String applicationName,

        @NotBlank(message = "environment is required")
        String environment,

        @NotBlank(message = "query is required")
        String query,

        List<DocumentType> documentTypes,
        Integer topK
) {}
