package com.example.prodsupport.ai.tools.model;

public record ApplicationInfoData(
        String applicationName,
        String team,
        String environment,
        String description,
        boolean enabled
) {}
