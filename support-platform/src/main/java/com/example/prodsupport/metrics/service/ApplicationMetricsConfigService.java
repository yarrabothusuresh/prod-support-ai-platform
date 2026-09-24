package com.example.prodsupport.metrics.service;

import com.example.prodsupport.common.exception.ApplicationNotFoundException;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.metrics.dto.ApplicationMetricsConfigDto;
import com.example.prodsupport.metrics.dto.UpdateMetricsConfigRequest;
import com.example.prodsupport.metrics.entity.ApplicationMetricsConfigEntity;
import com.example.prodsupport.metrics.repository.ApplicationMetricsConfigRepository;
import com.example.prodsupport.infrastructure.repository.RegisteredApplicationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class ApplicationMetricsConfigService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationMetricsConfigService.class);

    private final ApplicationMetricsConfigRepository configRepository;
    private final RegisteredApplicationRepository applicationRepository;

    public ApplicationMetricsConfigService(ApplicationMetricsConfigRepository configRepository,
                                           RegisteredApplicationRepository applicationRepository) {
        this.configRepository = configRepository;
        this.applicationRepository = applicationRepository;
    }

    public ApplicationMetricsConfigDto getConfig(Long applicationId) {
        RegisteredApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ApplicationNotFoundException(applicationId));

        ApplicationMetricsConfigEntity entity = configRepository.findByApplication(app)
                .orElseGet(() -> createDefaultConfig(app));

        return toDto(entity);
    }

    public Optional<ApplicationMetricsConfigEntity> findEntityByApplication(RegisteredApplication app) {
        return configRepository.findByApplication(app);
    }

    public boolean isMetricsConfiguredAndEnabled(RegisteredApplication app) {
        if (app == null) return false;
        return configRepository.findByApplication(app)
                .map(ApplicationMetricsConfigEntity::isMetricsEnabled)
                .orElse(true);
    }

    @Transactional
    public ApplicationMetricsConfigDto updateConfig(Long applicationId, UpdateMetricsConfigRequest request) {
        RegisteredApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ApplicationNotFoundException(applicationId));

        ApplicationMetricsConfigEntity entity = configRepository.findByApplication(app)
                .orElseGet(() -> new ApplicationMetricsConfigEntity(app, true, app.getApplicationName(), app.getApplicationName()));

        if (request.enabled() != null) {
            entity.setMetricsEnabled(request.enabled());
        }
        if (request.prometheusJob() != null && !request.prometheusJob().isBlank()) {
            entity.setPrometheusJob(request.prometheusJob().trim());
        }
        if (request.applicationLabel() != null && !request.applicationLabel().isBlank()) {
            entity.setApplicationLabel(request.applicationLabel().trim());
        }
        entity.setUpdatedAt(Instant.now());

        ApplicationMetricsConfigEntity saved = configRepository.save(entity);
        log.info("Updated metrics config for application id {}: enabled={}, job={}, label={}",
                applicationId, saved.isMetricsEnabled(), saved.getPrometheusJob(), saved.getApplicationLabel());

        return toDto(saved);
    }

    @Transactional
    public ApplicationMetricsConfigEntity createDefaultConfig(RegisteredApplication app) {
        ApplicationMetricsConfigEntity entity = new ApplicationMetricsConfigEntity(
                app,
                true,
                app.getApplicationName(),
                app.getApplicationName()
        );
        return configRepository.save(entity);
    }

    private ApplicationMetricsConfigDto toDto(ApplicationMetricsConfigEntity entity) {
        RegisteredApplication app = entity.getApplication();
        return new ApplicationMetricsConfigDto(
                entity.getId(),
                app.getId(),
                app.getApplicationName(),
                app.getEnvironment(),
                entity.isMetricsEnabled(),
                entity.getPrometheusJob(),
                entity.getApplicationLabel()
        );
    }
}
