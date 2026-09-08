package com.example.prodsupport.infrastructure.client;

import com.example.prodsupport.application.dto.TestConnectionResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Component
public class SupportClient {

    private static final Logger log = LoggerFactory.getLogger(SupportClient.class);

    private final RestClient restClient;

    @Autowired
    public SupportClient(RestClient.Builder restClientBuilder) {
        this(restClientBuilder.build());
    }

    public SupportClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public TestConnectionResponse testConnection(String supportInfoUrl) {
        log.info("Testing connection to application support endpoint: {}", supportInfoUrl);
        long startTime = System.currentTimeMillis();

        try {
            SupportInfoPayload payload = restClient.get()
                    .uri(supportInfoUrl)
                    .retrieve()
                    .body(SupportInfoPayload.class);

            long responseTimeMs = System.currentTimeMillis() - startTime;
            String appName = payload != null ? payload.getApplicationName() : "Unknown";
            String status = payload != null && payload.getStatus() != null ? payload.getStatus() : "UP";

            log.info("Test connection successful for {} in {} ms. Status: {}", appName, responseTimeMs, status);
            return TestConnectionResponse.success(appName, status, responseTimeMs);

        } catch (Exception ex) {
            log.warn("Test connection failed for {}: {}", supportInfoUrl, ex.getMessage());
            return TestConnectionResponse.failure(cleanErrorMessage(ex));
        }
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
            return "Support endpoint not found (404)";
        }
        if (message.contains("500") || message.contains("Internal Server Error")) {
            return "Remote service error (500)";
        }
        if (message.contains("UnknownHostException")) {
            return "Host resolution failed";
        }
        return "Connection failed: " + message;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SupportInfoPayload {
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
}
