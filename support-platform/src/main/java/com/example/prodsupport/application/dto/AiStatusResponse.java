package com.example.prodsupport.application.dto;

public record AiStatusResponse(
        String provider,
        String model,
        boolean available
) {
}
