package com.example.prodsupport.database.entity;

import com.example.prodsupport.database.model.DatabaseType;
import com.example.prodsupport.domain.RegisteredApplication;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "application_database_config")
public class ApplicationDatabaseConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false, unique = true)
    private RegisteredApplication application;

    @Enumerated(EnumType.STRING)
    @Column(name = "database_type", nullable = false, length = 32)
    private DatabaseType databaseType = DatabaseType.POSTGRESQL;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(name = "jdbc_url", nullable = false, length = 512)
    private String jdbcUrl;

    @Column(name = "username", nullable = false, length = 128)
    private String username;

    @Column(name = "credential_reference", length = 128)
    private String credentialReference;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public ApplicationDatabaseConfigEntity() {
    }

    public ApplicationDatabaseConfigEntity(RegisteredApplication application,
                                           DatabaseType databaseType,
                                           String displayName,
                                           String jdbcUrl,
                                           String username,
                                           String credentialReference,
                                           boolean enabled) {
        this.application = application;
        this.databaseType = databaseType != null ? databaseType : DatabaseType.POSTGRESQL;
        this.displayName = displayName;
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.credentialReference = credentialReference;
        this.enabled = enabled;
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

    public DatabaseType getDatabaseType() {
        return databaseType;
    }

    public void setDatabaseType(DatabaseType databaseType) {
        this.databaseType = databaseType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public void setJdbcUrl(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getCredentialReference() {
        return credentialReference;
    }

    public void setCredentialReference(String credentialReference) {
        this.credentialReference = credentialReference;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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
