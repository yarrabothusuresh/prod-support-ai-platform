package com.example.prodsupport.metrics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "prod-support.metrics")
public class MetricsProperties {

    private boolean enabled = true;
    private String prometheusBaseUrl = "http://localhost:9090";
    private int defaultWindowMinutes = 15;
    private int maximumWindowHours = 24;
    private Duration queryTimeout = Duration.ofSeconds(5);
    private int maximumDataPoints = 500;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPrometheusBaseUrl() {
        return prometheusBaseUrl;
    }

    public void setPrometheusBaseUrl(String prometheusBaseUrl) {
        this.prometheusBaseUrl = prometheusBaseUrl;
    }

    public int getDefaultWindowMinutes() {
        return defaultWindowMinutes;
    }

    public void setDefaultWindowMinutes(int defaultWindowMinutes) {
        this.defaultWindowMinutes = defaultWindowMinutes;
    }

    public int getMaximumWindowHours() {
        return maximumWindowHours;
    }

    public void setMaximumWindowHours(int maximumWindowHours) {
        this.maximumWindowHours = maximumWindowHours;
    }

    public Duration getQueryTimeout() {
        return queryTimeout;
    }

    public void setQueryTimeout(Duration queryTimeout) {
        this.queryTimeout = queryTimeout;
    }

    public int getMaximumDataPoints() {
        return maximumDataPoints;
    }

    public void setMaximumDataPoints(int maximumDataPoints) {
        this.maximumDataPoints = maximumDataPoints;
    }
}
