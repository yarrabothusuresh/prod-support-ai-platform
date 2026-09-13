package com.example.prodsupport.ai.tools.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record KnowledgeSearchToolRequest(
        @JsonProperty(required = true)
        @JsonPropertyDescription("The registered application name to search documentation for, e.g. payment-service")
        String applicationName,

        @JsonProperty(required = true)
        @JsonPropertyDescription("The deployment environment, e.g. local or prod")
        String environment,

        @JsonProperty(required = true)
        @JsonPropertyDescription("The diagnostic knowledge query, e.g. database connection timeout recovery or connection pool exhaustion")
        String query,

        @JsonProperty(required = false)
        @JsonPropertyDescription("Maximum number of documentation excerpts to retrieve (1 to 10, default 5)")
        Integer topK
) {}
