package com.example.prodsupport.ai.service;

import com.example.prodsupport.ai.config.AiProperties;
import com.example.prodsupport.ai.model.ApplicationSupportContext;
import com.example.prodsupport.domain.RegisteredApplication;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ApplicationContextService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationContextService.class);

    private final RestClient restClient;
    private final AiProperties aiProperties;

    @org.springframework.beans.factory.annotation.Autowired
    public ApplicationContextService(RestClient.Builder restClientBuilder, AiProperties aiProperties) {
        this.aiProperties = aiProperties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeoutSec = aiProperties.getContextTimeoutSeconds() > 0 ? aiProperties.getContextTimeoutSeconds() : 3;
        requestFactory.setConnectTimeout(Duration.ofSeconds(timeoutSec));
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSec));

        this.restClient = restClientBuilder
                .requestFactory(requestFactory)
                .build();
    }

    // Constructor for testing with custom RestClient
    public ApplicationContextService(RestClient restClient, AiProperties aiProperties) {
        this.restClient = restClient;
        this.aiProperties = aiProperties;
    }

    public ApplicationSupportContext collectContext(RegisteredApplication app) {
        return collectContext(
                app.getApplicationName(),
                app.getTeam(),
                app.getEnvironment(),
                app.getDescription(),
                app.getBaseUrl(),
                app.getSupportInfoUrl(),
                app.getHealthUrl()
        );
    }

    public ApplicationSupportContext collectContext(String applicationName,
                                                   String team,
                                                   String environment,
                                                   String description,
                                                   String baseUrl,
                                                   String supportInfoUrl,
                                                   String healthUrl) {
        log.info("Collecting live support context for application: '{}/{}'", applicationName, environment);

        List<String> warnings = new ArrayList<>();
        String normalizedBaseUrl = baseUrl != null ? baseUrl.replaceAll("/+$", "") : "";

        String targetSupportUrl = (supportInfoUrl != null && !supportInfoUrl.isBlank())
                ? supportInfoUrl
                : normalizedBaseUrl + "/support/info";

        String targetHealthUrl = (healthUrl != null && !healthUrl.isBlank())
                ? healthUrl
                : normalizedBaseUrl + "/actuator/health";

        // 1. Fetch /support/info
        String supportStatus = "UNKNOWN";
        boolean supportInfoAvailable = false;
        try {
            log.debug("Calling support info endpoint: {}", targetSupportUrl);
            SupportInfoResponse payload = restClient.get()
                    .uri(targetSupportUrl)
                    .retrieve()
                    .body(SupportInfoResponse.class);

            if (payload != null && payload.getStatus() != null && !payload.getStatus().isBlank()) {
                supportStatus = payload.getStatus().toUpperCase();
            } else {
                supportStatus = "UP";
            }
            supportInfoAvailable = true;
            log.debug("Support info collected successfully: status={}", supportStatus);
        } catch (Exception ex) {
            String errorMsg = cleanErrorMessage(ex);
            log.warn("Failed to collect support info from {}: {}", targetSupportUrl, errorMsg);
            warnings.add("Unable to retrieve support info (" + errorMsg + ")");
        }

        // 2. Fetch /actuator/health
        String actuatorStatus = "UNKNOWN";
        boolean healthAvailable = false;
        try {
            log.debug("Calling actuator health endpoint: {}", targetHealthUrl);
            ActuatorHealthResponse health = restClient.get()
                    .uri(targetHealthUrl)
                    .retrieve()
                    .body(ActuatorHealthResponse.class);

            if (health != null && health.getStatus() != null && !health.getStatus().isBlank()) {
                actuatorStatus = health.getStatus().toUpperCase();
            } else {
                actuatorStatus = "UP";
            }
            healthAvailable = true;
            log.debug("Actuator health collected successfully: status={}", actuatorStatus);
        } catch (Exception ex) {
            String errorMsg = cleanErrorMessage(ex);
            log.warn("Failed to collect actuator health from {}: {}", targetHealthUrl, errorMsg);
            warnings.add("Unable to retrieve actuator health (" + errorMsg + ")");
        }

        log.info("Completed context collection for '{}/{}': support={}, health={}, warnings={}",
                applicationName, environment, supportStatus, actuatorStatus, warnings.size());

        return new ApplicationSupportContext(
                applicationName,
                team,
                environment,
                description,
                baseUrl,
                supportStatus,
                actuatorStatus,
                supportInfoAvailable,
                healthAvailable,
                OffsetDateTime.now(),
                warnings
        );
    }

    private String cleanErrorMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return "Connection failed";
        }
        if (message.contains("Connection refused")) {
            return "Connection refused";
        }
        if (message.contains("timed out") || message.contains("TimeoutException") || message.contains("SocketTimeoutException")) {
            return "Connection timed out";
        }
        if (message.contains("404")) {
            return "Endpoint not found (404)";
        }
        if (message.contains("500") || message.contains("Internal Server Error")) {
            return "Remote server error (500)";
        }
        return "Connection failed";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SupportInfoResponse {
        private String applicationName;
        private String status;

        public String getApplicationName() {
            return applicationName;
        }

        public void setApplicationName(String applicationName) {
            this.applicationName = applicationName;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ActuatorHealthResponse {
        private String status;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}
