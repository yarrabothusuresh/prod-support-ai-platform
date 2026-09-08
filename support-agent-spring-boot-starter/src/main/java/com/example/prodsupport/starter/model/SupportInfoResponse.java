package com.example.prodsupport.starter.model;

public class SupportInfoResponse {

    private String applicationName;
    private String team;
    private String environment;
    private String description;
    private String status;

    public SupportInfoResponse() {
    }

    public SupportInfoResponse(String applicationName, String team, String environment, String description, String status) {
        this.applicationName = applicationName;
        this.team = team;
        this.environment = environment;
        this.description = description;
        this.status = status;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
