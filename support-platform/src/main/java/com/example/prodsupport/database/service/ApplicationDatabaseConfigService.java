package com.example.prodsupport.database.service;

import com.example.prodsupport.common.exception.ApplicationNotFoundException;
import com.example.prodsupport.database.dto.DatabaseConfigDto;
import com.example.prodsupport.database.dto.DatabaseConfigRequest;
import com.example.prodsupport.database.entity.ApplicationDatabaseConfigEntity;
import com.example.prodsupport.database.factory.DiagnosticDataSourceFactory;
import com.example.prodsupport.database.repository.ApplicationDatabaseConfigRepository;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.infrastructure.repository.RegisteredApplicationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class ApplicationDatabaseConfigService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationDatabaseConfigService.class);

    private final ApplicationDatabaseConfigRepository dbConfigRepository;
    private final RegisteredApplicationRepository applicationRepository;
    private final DiagnosticDataSourceFactory dataSourceFactory;

    public ApplicationDatabaseConfigService(ApplicationDatabaseConfigRepository dbConfigRepository,
                                            RegisteredApplicationRepository applicationRepository,
                                            DiagnosticDataSourceFactory dataSourceFactory) {
        this.dbConfigRepository = dbConfigRepository;
        this.applicationRepository = applicationRepository;
        this.dataSourceFactory = dataSourceFactory;
    }

    @Transactional(readOnly = true)
    public Optional<DatabaseConfigDto> getDatabaseConfig(Long applicationId) {
        RegisteredApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ApplicationNotFoundException(applicationId));

        return dbConfigRepository.findByApplicationId(app.getId())
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<ApplicationDatabaseConfigEntity> getDatabaseConfigEntity(String applicationName, String environment) {
        return dbConfigRepository.findByApplicationApplicationNameAndApplicationEnvironment(
                applicationName.trim(), environment.trim());
    }

    @Transactional
    public DatabaseConfigDto saveOrUpdateDatabaseConfig(Long applicationId, DatabaseConfigRequest request) {
        RegisteredApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ApplicationNotFoundException(applicationId));

        if (request.jdbcUrl() == null || request.jdbcUrl().isBlank()) {
            throw new IllegalArgumentException("jdbcUrl must not be blank");
        }
        if (request.username() == null || request.username().isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }

        ApplicationDatabaseConfigEntity entity = dbConfigRepository.findByApplicationId(app.getId())
                .orElseGet(() -> new ApplicationDatabaseConfigEntity(
                        app,
                        request.databaseType(),
                        request.displayName(),
                        request.jdbcUrl(),
                        request.username(),
                        request.credentialReference(),
                        request.enabled() != null ? request.enabled() : true
                ));

        entity.setDatabaseType(request.databaseType());
        entity.setDisplayName(request.displayName());
        entity.setJdbcUrl(request.jdbcUrl());
        entity.setUsername(request.username());
        entity.setCredentialReference(request.credentialReference());
        if (request.enabled() != null) {
            entity.setEnabled(request.enabled());
        }
        entity.setUpdatedAt(Instant.now());

        ApplicationDatabaseConfigEntity saved = dbConfigRepository.save(entity);

        // Evict any cached DataSource so new configurations take effect
        if (saved.getId() != null) {
            dataSourceFactory.evict(saved.getId());
        }

        log.info("Saved database configuration for application id {} ('{}')",
                app.getId(), app.getApplicationName());

        return toDto(saved);
    }

    public DatabaseConfigDto toDto(ApplicationDatabaseConfigEntity entity) {
        return new DatabaseConfigDto(
                entity.getId(),
                entity.getApplication().getId(),
                entity.getDatabaseType(),
                entity.getDisplayName(),
                entity.getJdbcUrl(),
                entity.getUsername(),
                entity.getCredentialReference(), // note: reference string only, never the actual password
                entity.isEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
