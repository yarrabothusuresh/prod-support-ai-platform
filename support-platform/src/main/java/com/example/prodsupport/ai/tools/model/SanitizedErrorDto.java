package com.example.prodsupport.ai.tools.model;

public record SanitizedErrorDto(
        String timestamp,
        String type,
        String message
) {}
