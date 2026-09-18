package com.example.prodsupport.logging.entity;

import com.example.prodsupport.domain.RegisteredApplication;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "application_logging_config")
public class ApplicationLoggingConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false, unique = true)
    private RegisteredApplication application;

    @Column(name = "logging_enabled", nullable = false)
    private boolean loggingEnabled = true;

    @Column(name = "log_source", nullable = false, length = 64)
    private String logSource = "ELASTICSEARCH";

    @Column(name = "index_pattern", nullable = false, length = 128)
    private String indexPattern = "prod-support-logs-*";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public ApplicationLoggingConfigEntity() {
    }

    public ApplicationLoggingConfigEntity(RegisteredApplication application,
                                         boolean loggingEnabled,
                                         String logSource,
                                         String indexPattern) {
        this.application = application;
        this.loggingEnabled = loggingEnabled;
        this.logSource = logSource != null && !logSource.isBlank() ? logSource : "ELASTICSEARCH";
        this.indexPattern = indexPattern != null && !indexPattern.isBlank() ? indexPattern : "prod-support-logs-*";
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

    public boolean isLoggingEnabled() {
        return loggingEnabled;
    }

    public void setLoggingEnabled(boolean loggingEnabled) {
        this.loggingEnabled = loggingEnabled;
    }

    public String getLogSource() {
        return logSource;
    }

    public void setLogSource(String logSource) {
        this.logSource = logSource;
    }

    public String getIndexPattern() {
        return indexPattern;
    }

    public void setIndexPattern(String indexPattern) {
        this.indexPattern = indexPattern;
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
