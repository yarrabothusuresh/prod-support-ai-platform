package com.example.prodsupport.ai.tools.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record DependenciesRequest(
        @JsonProperty(required = true)
        @JsonPropertyDescription("The registered name of the application to check dependencies for, e.g. payment-service")
        String applicationName,

        @JsonProperty(required = true)
        @JsonPropertyDescription("The environment where the application is deployed, e.g. local")
        String environment
) {}
