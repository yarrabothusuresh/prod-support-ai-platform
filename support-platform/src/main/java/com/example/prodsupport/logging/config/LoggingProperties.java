package com.example.prodsupport.logging.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "prod-support.logging")
public class LoggingProperties {

    private boolean enabled = true;
    private String elasticsearchUrl = "http://localhost:9200";
    private int defaultWindowMinutes = 15;
    private int maximumWindowHours = 24;
    private int defaultResultLimit = 20;
    private int maximumResultLimit = 100;
    private Duration requestTimeout = Duration.ofSeconds(5);
    private String defaultIndexPattern = "prod-support-logs-*";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getElasticsearchUrl() {
        return elasticsearchUrl;
    }

    public void setElasticsearchUrl(String elasticsearchUrl) {
        this.elasticsearchUrl = elasticsearchUrl;
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

    public int getDefaultResultLimit() {
        return defaultResultLimit;
    }

    public void setDefaultResultLimit(int defaultResultLimit) {
        this.defaultResultLimit = defaultResultLimit;
    }

    public int getMaximumResultLimit() {
        return maximumResultLimit;
    }

    public void setMaximumResultLimit(int maximumResultLimit) {
        this.maximumResultLimit = maximumResultLimit;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public String getDefaultIndexPattern() {
        return defaultIndexPattern;
    }

    public void setDefaultIndexPattern(String defaultIndexPattern) {
        this.defaultIndexPattern = defaultIndexPattern;
    }
}
