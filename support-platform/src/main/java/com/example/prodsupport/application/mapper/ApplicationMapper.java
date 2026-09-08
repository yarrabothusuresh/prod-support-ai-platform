package com.example.prodsupport.application.mapper;

import com.example.prodsupport.application.dto.RegisterApplicationRequest;
import com.example.prodsupport.application.dto.RegisteredApplicationResponse;
import com.example.prodsupport.domain.RegisteredApplication;
import org.springframework.stereotype.Component;

@Component
public class ApplicationMapper {

    public RegisteredApplication toEntity(RegisterApplicationRequest request) {
        String cleanBaseUrl = cleanUrl(request.getBaseUrl());
        String healthUrl = cleanBaseUrl + "/actuator/health";
        String supportInfoUrl = cleanBaseUrl + "/support/info";

        return new RegisteredApplication(
                request.getApplicationName().trim(),
                request.getTeam() != null ? request.getTeam().trim() : null,
                request.getEnvironment().trim(),
                request.getDescription() != null ? request.getDescription().trim() : null,
                cleanBaseUrl,
                healthUrl,
                supportInfoUrl,
                true
        );
    }

    public RegisteredApplicationResponse toResponse(RegisteredApplication entity) {
        return new RegisteredApplicationResponse(
                entity.getId(),
                entity.getApplicationName(),
                entity.getTeam(),
                entity.getEnvironment(),
                entity.getDescription(),
                entity.getBaseUrl(),
                entity.getHealthUrl(),
                entity.getSupportInfoUrl(),
                entity.isEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String cleanUrl(String url) {
        if (url == null) {
            return "";
        }
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
