package com.example.prodsupport.ai.tools.model;

public record ApplicationHealthData(
        String status,
        String source,
        String collectedAt
) {}
