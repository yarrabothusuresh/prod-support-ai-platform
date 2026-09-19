package com.example.prodsupport.ai.tools.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record SearchApplicationErrorsRequest(
        @JsonPropertyDescription("The registered name of the target application, e.g. payment-service")
        String applicationName,

        @JsonPropertyDescription("The deployment environment, e.g. local or production")
        String environment,

        @JsonPropertyDescription("Lookback window in minutes (default 15, max 1440)")
        Integer minutes,

        @JsonPropertyDescription("Optional safe keyword to filter error messages")
        String keyword,

        @JsonPropertyDescription("Optional trace ID to find errors specifically related to a distributed trace")
        String traceId
) {
    public SearchApplicationErrorsRequest(String applicationName, String environment, Integer minutes, String keyword) {
        this(applicationName, environment, minutes, keyword, null);
    }
}
