package com.example.prodsupport.ai.tools.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record SearchApplicationTracesRequest(
        @JsonPropertyDescription("The registered name of the target application, e.g. payment-service")
        String applicationName,

        @JsonPropertyDescription("The deployment environment, e.g. local or production")
        String environment,

        @JsonPropertyDescription("Lookback window in minutes (default 15, max 120)")
        Integer minutes,

        @JsonPropertyDescription("Whether to filter only traces containing errors (default false)")
        Boolean errorOnly
) {}
