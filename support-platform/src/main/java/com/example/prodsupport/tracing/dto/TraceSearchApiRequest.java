package com.example.prodsupport.tracing.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record TraceSearchApiRequest(
        @Min(value = 1, message = "Window minutes must be at least 1")
        @Max(value = 120, message = "Window minutes cannot exceed 120")
        Integer minutes,

        @Min(value = 1, message = "Limit must be at least 1")
        @Max(value = 50, message = "Limit cannot exceed 50")
        Integer limit,

        Boolean errorOnly
) {}
