package com.example.prodsupport.ai.tools.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record ErrorPatternSummaryRequest(
        @JsonPropertyDescription("The registered name of the target application, e.g. payment-service")
        String applicationName,

        @JsonPropertyDescription("The deployment environment, e.g. local or production")
        String environment,

        @JsonPropertyDescription("Lookback window in minutes (default 15, max 1440)")
        Integer minutes
) {}
