package com.example.prodsupport.ai.tools.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record GetApplicationMetricsRequest(
        @JsonProperty(required = true)
        @JsonPropertyDescription("The registered application name (e.g. payment-service)")
        String applicationName,

        @JsonProperty(required = true)
        @JsonPropertyDescription("The environment name (e.g. local, dev, prod)")
        String environment,

        @JsonProperty(defaultValue = "15")
        @JsonPropertyDescription("Time window in minutes for metrics calculation (default 15)")
        Integer minutes
) {}
