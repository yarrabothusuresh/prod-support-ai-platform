package com.example.prodsupport.ai.tools.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record GetTraceDetailsRequest(
        @JsonPropertyDescription("The registered name of the target application, e.g. payment-service")
        String applicationName,

        @JsonPropertyDescription("The deployment environment, e.g. local or production")
        String environment,

        @JsonPropertyDescription("The 16 or 32 character hex trace ID to retrieve details for")
        String traceId
) {}
