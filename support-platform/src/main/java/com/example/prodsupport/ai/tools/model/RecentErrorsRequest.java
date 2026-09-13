package com.example.prodsupport.ai.tools.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record RecentErrorsRequest(
        @JsonProperty(required = true)
        @JsonPropertyDescription("The registered name of the application to inspect errors for, e.g. payment-service")
        String applicationName,

        @JsonProperty(required = true)
        @JsonPropertyDescription("The environment where the application is deployed, e.g. local")
        String environment,

        @JsonPropertyDescription("The maximum number of recent errors to retrieve (min: 1, max: 50, default: 10)")
        Integer limit
) {}
