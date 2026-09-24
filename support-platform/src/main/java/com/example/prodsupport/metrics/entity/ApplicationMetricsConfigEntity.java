package com.example.prodsupport.metrics.entity;

import com.example.prodsupport.domain.RegisteredApplication;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "application_metrics_config")
public class ApplicationMetricsConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false, unique = true)
    private RegisteredApplication application;

    @Column(name = "metrics_enabled", nullable = false)
    private boolean metricsEnabled = true;

    @Column(name = "prometheus_job", nullable = false, length = 100)
    private String prometheusJob;

    @Column(name = "application_label", nullable = false, length = 100)
    private String applicationLabel;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public ApplicationMetricsConfigEntity() {
    }

    public ApplicationMetricsConfigEntity(RegisteredApplication application,
                                         boolean metricsEnabled,
                                         String prometheusJob,
                                         String applicationLabel) {
        this.application = application;
        this.metricsEnabled = metricsEnabled;
        this.prometheusJob = (prometheusJob != null && !prometheusJob.isBlank())
                ? prometheusJob.trim() : application.getApplicationName();
        this.applicationLabel = (applicationLabel != null && !applicationLabel.isBlank())
                ? applicationLabel.trim() : application.getApplicationName();
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

    public boolean isMetricsEnabled() {
        return metricsEnabled;
    }

    public void setMetricsEnabled(boolean metricsEnabled) {
        this.metricsEnabled = metricsEnabled;
    }

    public String getPrometheusJob() {
        return prometheusJob;
    }

    public void setPrometheusJob(String prometheusJob) {
        this.prometheusJob = prometheusJob;
    }

    public String getApplicationLabel() {
        return applicationLabel;
    }

    public void setApplicationLabel(String applicationLabel) {
        this.applicationLabel = applicationLabel;
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
