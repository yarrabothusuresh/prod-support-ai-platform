package com.example.prodsupport.tracing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "prod-support.tracing")
public class TracingProperties {

    private boolean enabled = true;
    private String jaegerBaseUrl = "http://localhost:16686";
    private int defaultWindowMinutes = 15;
    private int maximumWindowMinutes = 120;
    private int defaultResultLimit = 20;
    private int maximumResultLimit = 50;
    private Duration requestTimeout = Duration.ofSeconds(3);
    private Duration searchTimeout = Duration.ofSeconds(3);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getJaegerBaseUrl() {
        return jaegerBaseUrl;
    }

    public void setJaegerBaseUrl(String jaegerBaseUrl) {
        this.jaegerBaseUrl = jaegerBaseUrl;
    }

    public int getDefaultWindowMinutes() {
        return defaultWindowMinutes;
    }

    public void setDefaultWindowMinutes(int defaultWindowMinutes) {
        this.defaultWindowMinutes = defaultWindowMinutes;
    }

    public int getMaximumWindowMinutes() {
        return maximumWindowMinutes;
    }

    public void setMaximumWindowMinutes(int maximumWindowMinutes) {
        this.maximumWindowMinutes = maximumWindowMinutes;
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

    public Duration getSearchTimeout() {
        return searchTimeout;
    }

    public void setSearchTimeout(Duration searchTimeout) {
        this.searchTimeout = searchTimeout;
    }
}
