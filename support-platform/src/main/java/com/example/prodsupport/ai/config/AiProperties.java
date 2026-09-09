package com.example.prodsupport.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "prod-support.ai")
public class AiProperties {

    /**
     * AI provider name, e.g. "ollama".
     */
    private String provider = "ollama";

    /**
     * Default model name to use.
     */
    private String model = "llama3:latest";

    /**
     * Base URL for the AI provider.
     */
    private String baseUrl = "http://localhost:11434";

    /**
     * Timeout in seconds for AI calls.
     */
    private int timeoutSeconds = 30;

    /**
     * Flag to control sensitive prompt logging. Default is false (OFF).
     */
    private boolean logPrompt = false;

    /**
     * Context collection HTTP timeout in seconds (connect and read).
     */
    private int contextTimeoutSeconds = 3;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public boolean isLogPrompt() {
        return logPrompt;
    }

    public void setLogPrompt(boolean logPrompt) {
        this.logPrompt = logPrompt;
    }

    public int getContextTimeoutSeconds() {
        return contextTimeoutSeconds;
    }

    public void setContextTimeoutSeconds(int contextTimeoutSeconds) {
        this.contextTimeoutSeconds = contextTimeoutSeconds;
    }
}
