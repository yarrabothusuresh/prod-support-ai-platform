package com.example.prodsupport.application.service;

import com.example.prodsupport.ai.config.AiProperties;
import com.example.prodsupport.ai.tools.model.*;
import com.example.prodsupport.domain.RegisteredApplication;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class DiagnosticService {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticService.class);

    private static final Pattern SENSITIVE_DATA_PATTERN = Pattern.compile(
            "(?i)(password|secret|token|authorization|apikey|key|bearer)\\s*[:=]\\s*[^,;\\s]+");

    private final RestClient restClient;
    private final AiProperties aiProperties;

    @org.springframework.beans.factory.annotation.Autowired
    public DiagnosticService(RestClient.Builder restClientBuilder, AiProperties aiProperties) {
        this.aiProperties = aiProperties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeoutSec = aiProperties.getDiagnosticTimeoutSeconds() > 0 ? aiProperties.getDiagnosticTimeoutSeconds() : 3;
        requestFactory.setConnectTimeout(Duration.ofSeconds(timeoutSec));
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSec));

        this.restClient = restClientBuilder
                .requestFactory(requestFactory)
                .build();
    }

    // Constructor for testing with custom RestClient
    public DiagnosticService(RestClient restClient, AiProperties aiProperties) {
        this.restClient = restClient;
        this.aiProperties = aiProperties;
    }

    public ApplicationInfoData getApplicationInfo(RegisteredApplication app) {
        String targetUrl = resolveSupportInfoUrl(app);
        log.debug("Fetching application info from: {}", targetUrl);

        try {
            SupportInfoResponse response = restClient.get()
                    .uri(targetUrl)
                    .retrieve()
                    .body(SupportInfoResponse.class);

            return new ApplicationInfoData(
                    app.getApplicationName(),
                    app.getTeam(),
                    app.getEnvironment(),
                    app.getDescription(),
                    app.isEnabled()
            );
        } catch (Exception ex) {
            log.warn("Failed to reach support info endpoint {}: {}", targetUrl, ex.getMessage());
            // Even if remote endpoint failed, return registered metadata from registry
            return new ApplicationInfoData(
                    app.getApplicationName(),
                    app.getTeam(),
                    app.getEnvironment(),
                    app.getDescription(),
                    app.isEnabled()
            );
        }
    }

    public ApplicationHealthData checkHealth(RegisteredApplication app) {
        String targetUrl = resolveHealthUrl(app);
        log.debug("Checking application health at: {}", targetUrl);

        try {
            ActuatorHealthResponse health = restClient.get()
                    .uri(targetUrl)
                    .retrieve()
                    .body(ActuatorHealthResponse.class);

            String status = (health != null && health.getStatus() != null && !health.getStatus().isBlank())
                    ? health.getStatus().toUpperCase()
                    : "UNKNOWN";

            return new ApplicationHealthData(status, "/actuator/health", Instant.now().toString());
        } catch (Exception ex) {
            String errorMsg = cleanErrorMessage(ex);
            log.warn("Actuator health check failed for {}: {}", targetUrl, errorMsg);
            throw new DiagnosticExecutionException("Actuator health check failed: " + errorMsg, ex);
        }
    }

    public RecentErrorsData getRecentErrors(RegisteredApplication app, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        String targetUrl = resolveBaseUrl(app) + "/support/errors?limit=" + safeLimit;
        log.debug("Fetching recent errors from: {}", targetUrl);

        try {
            SupportErrorsResponsePayload payload = restClient.get()
                    .uri(targetUrl)
                    .retrieve()
                    .body(SupportErrorsResponsePayload.class);

            List<SanitizedErrorDto> sanitized = new ArrayList<>();
            if (payload != null && payload.getErrors() != null) {
                for (SupportErrorItem item : payload.getErrors()) {
                    sanitized.add(new SanitizedErrorDto(
                            item.getTimestamp() != null ? item.getTimestamp() : Instant.now().toString(),
                            sanitizeString(item.getType() != null ? item.getType() : "UnknownException"),
                            sanitizeString(item.getMessage() != null ? item.getMessage() : "Error occurred")
                    ));
                }
            }
            return new RecentErrorsData(sanitized.size(), sanitized);
        } catch (Exception ex) {
            String errorMsg = cleanErrorMessage(ex);
            log.warn("Recent errors fetch failed for {}: {}", targetUrl, errorMsg);
            throw new DiagnosticExecutionException("Recent errors check failed: " + errorMsg, ex);
        }
    }

    public DependenciesData checkDependencies(RegisteredApplication app) {
        String targetUrl = resolveBaseUrl(app) + "/support/dependencies";
        log.debug("Checking dependencies at: {}", targetUrl);

        try {
            SupportDependenciesResponsePayload payload = restClient.get()
                    .uri(targetUrl)
                    .retrieve()
                    .body(SupportDependenciesResponsePayload.class);

            List<DependencyItemDto> items = new ArrayList<>();
            if (payload != null && payload.getDependencies() != null) {
                for (SupportDependencyItem item : payload.getDependencies()) {
                    items.add(new DependencyItemDto(
                            item.getName() != null ? item.getName() : "unknown",
                            item.getType() != null ? item.getType() : "HTTP",
                            item.getStatus() != null ? item.getStatus().toUpperCase() : "UNKNOWN"
                    ));
                }
            }
            return new DependenciesData(items);
        } catch (Exception ex) {
            String errorMsg = cleanErrorMessage(ex);
            log.warn("Dependency check failed for {}: {}", targetUrl, errorMsg);
            throw new DiagnosticExecutionException("Dependency check failed: " + errorMsg, ex);
        }
    }

    public String cleanErrorMessage(Exception ex) {
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

    private String sanitizeString(String raw) {
        if (raw == null) {
            return "";
        }
        // Mask passwords, tokens, secrets
        return SENSITIVE_DATA_PATTERN.matcher(raw).replaceAll("$1=***");
    }

    private String resolveBaseUrl(RegisteredApplication app) {
        String baseUrl = app.getBaseUrl();
        return baseUrl != null ? baseUrl.replaceAll("/+$", "") : "";
    }

    private String resolveHealthUrl(RegisteredApplication app) {
        if (app.getHealthUrl() != null && !app.getHealthUrl().isBlank()) {
            return app.getHealthUrl();
        }
        return resolveBaseUrl(app) + "/actuator/health";
    }

    private String resolveSupportInfoUrl(RegisteredApplication app) {
        if (app.getSupportInfoUrl() != null && !app.getSupportInfoUrl().isBlank()) {
            return app.getSupportInfoUrl();
        }
        return resolveBaseUrl(app) + "/support/info";
    }

    public static class DiagnosticExecutionException extends RuntimeException {
        public DiagnosticExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SupportErrorsResponsePayload {
        private String applicationName;
        private List<SupportErrorItem> errors;

        public String getApplicationName() {
            return applicationName;
        }

        public void setApplicationName(String applicationName) {
            this.applicationName = applicationName;
        }

        public List<SupportErrorItem> getErrors() {
            return errors;
        }

        public void setErrors(List<SupportErrorItem> errors) {
            this.errors = errors;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SupportErrorItem {
        private String timestamp;
        private String level;
        private String type;
        private String message;

        public String getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }

        public String getLevel() {
            return level;
        }

        public void setLevel(String level) {
            this.level = level;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SupportDependenciesResponsePayload {
        private String applicationName;
        private List<SupportDependencyItem> dependencies;

        public String getApplicationName() {
            return applicationName;
        }

        public void setApplicationName(String applicationName) {
            this.applicationName = applicationName;
        }

        public List<SupportDependencyItem> getDependencies() {
            return dependencies;
        }

        public void setDependencies(List<SupportDependencyItem> dependencies) {
            this.dependencies = dependencies;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SupportDependencyItem {
        private String name;
        private String type;
        private String status;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}
