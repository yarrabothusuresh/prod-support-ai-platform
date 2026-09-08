package com.example.prodsupport.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegisterApplicationRequest {

    @NotBlank(message = "applicationName is required")
    @Size(max = 100, message = "applicationName must be less than 100 characters")
    private String applicationName;

    @Size(max = 100, message = "team must be less than 100 characters")
    private String team;

    @NotBlank(message = "environment is required")
    @Size(max = 50, message = "environment must be less than 50 characters")
    private String environment;

    @Size(max = 500, message = "description must be less than 500 characters")
    private String description;

    @NotBlank(message = "baseUrl is required")
    @Size(max = 255, message = "baseUrl must be less than 255 characters")
    private String baseUrl;

    public RegisterApplicationRequest() {
    }

    public RegisterApplicationRequest(String applicationName, String team, String environment,
                                      String description, String baseUrl) {
        this.applicationName = applicationName;
        this.team = team;
        this.environment = environment;
        this.description = description;
        this.baseUrl = baseUrl;
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
}
