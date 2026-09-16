package com.example.prodsupport.ai.tools.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record DatabaseDiagnosticToolRequest(
        @JsonProperty(required = true)
        @JsonPropertyDescription("The registered application name (e.g. payment-service)")
        String applicationName,

        @JsonProperty(required = true)
        @JsonPropertyDescription("The environment to inspect (e.g. local, dev, prod)")
        String environment
) {
}
