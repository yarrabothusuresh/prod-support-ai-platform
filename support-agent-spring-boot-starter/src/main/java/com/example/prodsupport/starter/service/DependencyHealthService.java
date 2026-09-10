package com.example.prodsupport.starter.service;

import com.example.prodsupport.starter.model.SupportDependency;
import com.example.prodsupport.starter.properties.SupportProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DependencyHealthService {

    private static final Logger log = LoggerFactory.getLogger(DependencyHealthService.class);
    private static final int HEALTH_CHECK_TIMEOUT_MS = 1500;

    private final SupportProperties properties;
    private final Map<String, String> statusOverrides = new ConcurrentHashMap<>();

    public DependencyHealthService(SupportProperties properties) {
        this.properties = properties;
    }

    public List<SupportDependency> checkDependencies() {
        List<SupportDependency> results = new ArrayList<>();
        List<SupportProperties.DependencyConfig> configs = properties.getDependencies();

        if (configs == null || configs.isEmpty()) {
            return results;
        }

        for (SupportProperties.DependencyConfig config : configs) {
            String name = config.getName();
            String type = config.getType() != null ? config.getType() : "HTTP";

            // Check if there is an explicit override (e.g., fault injection)
            if (statusOverrides.containsKey(name)) {
                String overrideStatus = statusOverrides.get(name);
                results.add(new SupportDependency(name, type, overrideStatus));
                continue;
            }

            // Check healthUrl if provided
            String healthUrl = config.getHealthUrl();
            if (healthUrl != null && !healthUrl.isBlank()) {
                String status = probeHealthUrl(healthUrl);
                results.add(new SupportDependency(name, type, status));
            } else {
                String status = config.getStatus() != null && !config.getStatus().isBlank()
                        ? config.getStatus()
                        : "UP";
                results.add(new SupportDependency(name, type, status));
            }
        }

        return results;
    }

    public void setDependencyOverride(String dependencyName, String status) {
        statusOverrides.put(dependencyName, status);
        log.info("Set dependency override for '{}' -> '{}'", dependencyName, status);
    }

    public void clearOverrides() {
        statusOverrides.clear();
        log.info("Cleared all dependency overrides");
    }

    private String probeHealthUrl(String healthUrl) {
        HttpURLConnection connection = null;
        try {
            URL url = URI.create(healthUrl).toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(HEALTH_CHECK_TIMEOUT_MS);
            connection.setReadTimeout(HEALTH_CHECK_TIMEOUT_MS);
            connection.connect();

            int code = connection.getResponseCode();
            return (code >= 200 && code < 300) ? "UP" : "DOWN";
        } catch (IOException ex) {
            log.debug("Health probe to {} failed: {}", healthUrl, ex.getMessage());
            return "DOWN";
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
