package com.example.prodsupport.knowledge.dto;

import jakarta.validation.constraints.NotBlank;

public record SupportKnowledgeChatRequest(
        @NotBlank(message = "applicationName is required")
        String applicationName,

        @NotBlank(message = "environment is required")
        String environment,

        @NotBlank(message = "question is required")
        String question
) {}
