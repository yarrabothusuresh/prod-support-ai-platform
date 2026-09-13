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

    /**
     * Diagnostic tool HTTP timeout in seconds.
     */
    private int diagnosticTimeoutSeconds = 3;

    /**
     * Maximum tool calls allowed in a single agentic investigation.
     */
    private int maxToolCalls = 6;

    /**
     * Tool calling configuration.
     */
    private ToolCalling toolCalling = new ToolCalling();

    public static class ToolCalling {
        private boolean enabled = true;
        private boolean deterministicFallback = true;
        private int maxToolCalls = 6;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isDeterministicFallback() {
            return deterministicFallback;
        }

        public void setDeterministicFallback(boolean deterministicFallback) {
            this.deterministicFallback = deterministicFallback;
        }

        public int getMaxToolCalls() {
            return maxToolCalls;
        }

        public void setMaxToolCalls(int maxToolCalls) {
            this.maxToolCalls = maxToolCalls;
        }
    }

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

    public int getDiagnosticTimeoutSeconds() {
        return diagnosticTimeoutSeconds;
    }

    public void setDiagnosticTimeoutSeconds(int diagnosticTimeoutSeconds) {
        this.diagnosticTimeoutSeconds = diagnosticTimeoutSeconds;
    }

    public int getMaxToolCalls() {
        return toolCalling != null && toolCalling.getMaxToolCalls() > 0 ? toolCalling.getMaxToolCalls() : maxToolCalls;
    }

    public void setMaxToolCalls(int maxToolCalls) {
        this.maxToolCalls = maxToolCalls;
        if (this.toolCalling != null) {
            this.toolCalling.setMaxToolCalls(maxToolCalls);
        }
    }

    public ToolCalling getToolCalling() {
        return toolCalling;
    }

    public void setToolCalling(ToolCalling toolCalling) {
        this.toolCalling = toolCalling;
    }
}
