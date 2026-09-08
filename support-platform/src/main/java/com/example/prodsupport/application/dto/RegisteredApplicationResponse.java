package com.example.prodsupport.application.dto;

import java.time.OffsetDateTime;

public class RegisteredApplicationResponse {

    private Long id;
    private String applicationName;
    private String team;
    private String environment;
    private String description;
    private String baseUrl;
    private String healthUrl;
    private String supportInfoUrl;
    private boolean enabled;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public RegisteredApplicationResponse() {
    }

    public RegisteredApplicationResponse(Long id, String applicationName, String team, String environment,
                                         String description, String baseUrl, String healthUrl,
                                         String supportInfoUrl, boolean enabled,
                                         OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.applicationName = applicationName;
        this.team = team;
        this.environment = environment;
        this.description = description;
        this.baseUrl = baseUrl;
        this.healthUrl = healthUrl;
        this.supportInfoUrl = supportInfoUrl;
        this.enabled = enabled;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public String getTeam() {
        return team;
    }

    public void setTeam(String team) {
        this.team = team;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getHealthUrl() {
        return healthUrl;
    }

    public void setHealthUrl(String healthUrl) {
        this.healthUrl = healthUrl;
    }

    public String getSupportInfoUrl() {
        return supportInfoUrl;
    }

    public void setSupportInfoUrl(String supportInfoUrl) {
        this.supportInfoUrl = supportInfoUrl;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
