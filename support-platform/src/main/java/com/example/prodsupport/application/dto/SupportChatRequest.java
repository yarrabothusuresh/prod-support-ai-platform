package com.example.prodsupport.application.dto;

import jakarta.validation.constraints.NotBlank;

public record SupportChatRequest(
        @NotBlank(message = "applicationName is required")
        String applicationName,

        @NotBlank(message = "environment is required")
        String environment,

        @NotBlank(message = "question is required")
        String question
) {
}
