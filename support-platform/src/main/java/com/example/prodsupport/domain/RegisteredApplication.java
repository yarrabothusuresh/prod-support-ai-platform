package com.example.prodsupport.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(
        name = "registered_application",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_registered_application_name_env", columnNames = {"application_name", "environment"})
        }
)
public class RegisteredApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_name", nullable = false, length = 100)
    private String applicationName;

    @Column(name = "team", length = 100)
    private String team;

    @Column(name = "environment", nullable = false, length = 50)
    private String environment;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "base_url", nullable = false, length = 255)
    private String baseUrl;

    @Column(name = "health_url", nullable = false, length = 255)
    private String healthUrl;

    @Column(name = "support_info_url", nullable = false, length = 255)
    private String supportInfoUrl;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public RegisteredApplication() {
    }

    public RegisteredApplication(String applicationName, String team, String environment,
                                 String description, String baseUrl, String healthUrl,
                                 String supportInfoUrl, boolean enabled) {
        this.applicationName = applicationName;
        this.team = team;
        this.environment = environment;
        this.description = description;
        this.baseUrl = baseUrl;
        this.healthUrl = healthUrl;
        this.supportInfoUrl = supportInfoUrl;
        this.enabled = enabled;
    }

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
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
