package com.example.prodsupport.tracing.entity;

import com.example.prodsupport.domain.RegisteredApplication;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "application_tracing_config")
public class ApplicationTracingConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false, unique = true)
    private RegisteredApplication application;

    @Column(name = "tracing_enabled", nullable = false)
    private boolean tracingEnabled = true;

    @Column(name = "tracing_service_name", nullable = false, length = 100)
    private String tracingServiceName;

    @Column(name = "tracing_environment", nullable = false, length = 50)
    private String tracingEnvironment;

    @Column(name = "jaeger_query_base_url", nullable = false, length = 255)
    private String jaegerQueryBaseUrl = "http://localhost:16686";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public ApplicationTracingConfigEntity() {
    }

    public ApplicationTracingConfigEntity(RegisteredApplication application,
                                          boolean tracingEnabled,
                                          String tracingServiceName,
                                          String tracingEnvironment,
                                          String jaegerQueryBaseUrl) {
        this.application = application;
        this.tracingEnabled = tracingEnabled;
        this.tracingServiceName = tracingServiceName != null && !tracingServiceName.isBlank()
                ? tracingServiceName.trim() : application.getApplicationName();
        this.tracingEnvironment = tracingEnvironment != null && !tracingEnvironment.isBlank()
                ? tracingEnvironment.trim() : application.getEnvironment();
        this.jaegerQueryBaseUrl = jaegerQueryBaseUrl != null && !jaegerQueryBaseUrl.isBlank()
                ? jaegerQueryBaseUrl.trim() : "http://localhost:16686";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public RegisteredApplication getApplication() {
        return application;
    }

    public void setApplication(RegisteredApplication application) {
        this.application = application;
    }

    public boolean isTracingEnabled() {
        return tracingEnabled;
    }

    public void setTracingEnabled(boolean tracingEnabled) {
        this.tracingEnabled = tracingEnabled;
    }

    public String getTracingServiceName() {
        return tracingServiceName;
    }

    public void setTracingServiceName(String tracingServiceName) {
        this.tracingServiceName = tracingServiceName;
    }

    public String getTracingEnvironment() {
        return tracingEnvironment;
    }

    public void setTracingEnvironment(String tracingEnvironment) {
        this.tracingEnvironment = tracingEnvironment;
    }

    public String getJaegerQueryBaseUrl() {
        return jaegerQueryBaseUrl;
    }

    public void setJaegerQueryBaseUrl(String jaegerQueryBaseUrl) {
        this.jaegerQueryBaseUrl = jaegerQueryBaseUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
