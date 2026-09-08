package com.example.prodsupport.application.service;

import com.example.prodsupport.application.dto.RegisterApplicationRequest;
import com.example.prodsupport.application.dto.RegisteredApplicationResponse;
import com.example.prodsupport.application.dto.TestConnectionResponse;
import com.example.prodsupport.application.mapper.ApplicationMapper;
import com.example.prodsupport.common.exception.ApplicationNotFoundException;
import com.example.prodsupport.common.exception.DuplicateApplicationException;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.infrastructure.client.SupportClient;
import com.example.prodsupport.infrastructure.repository.RegisteredApplicationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ApplicationRegistryService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationRegistryService.class);

    private final RegisteredApplicationRepository repository;
    private final ApplicationMapper mapper;
    private final SupportClient supportClient;

    public ApplicationRegistryService(RegisteredApplicationRepository repository,
                                      ApplicationMapper mapper,
                                      SupportClient supportClient) {
        this.repository = repository;
        this.mapper = mapper;
        this.supportClient = supportClient;
    }

    public RegisteredApplicationResponse registerApplication(RegisterApplicationRequest request) {
        String appName = request.getApplicationName().trim();
        String environment = request.getEnvironment().trim();

        log.info("Attempting to register application: '{}' in environment: '{}'", appName, environment);

        if (repository.existsByApplicationNameAndEnvironment(appName, environment)) {
            log.warn("Duplicate registration rejected: '{}' in environment: '{}'", appName, environment);
            throw new DuplicateApplicationException(appName, environment);
        }

        RegisteredApplication entity = mapper.toEntity(request);
        RegisteredApplication saved = repository.save(entity);

        log.info("Successfully registered application '{}' (id: {}) in environment '{}'",
                saved.getApplicationName(), saved.getId(), saved.getEnvironment());

        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<RegisteredApplicationResponse> getAllApplications() {
        return repository.findAll().stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RegisteredApplicationResponse getApplicationById(Long id) {
        RegisteredApplication entity = repository.findById(id)
                .orElseThrow(() -> new ApplicationNotFoundException(id));
        return mapper.toResponse(entity);
    }

    public void deleteApplication(Long id) {
        log.info("Attempting to delete application id: {}", id);
        RegisteredApplication entity = repository.findById(id)
                .orElseThrow(() -> new ApplicationNotFoundException(id));
        repository.delete(entity);
        log.info("Deleted application id: {} ('{}')", id, entity.getApplicationName());
    }

    public TestConnectionResponse testConnection(Long id) {
        RegisteredApplication entity = repository.findById(id)
                .orElseThrow(() -> new ApplicationNotFoundException(id));

        log.info("Triggering test connection for application id: {} ('{}') at {}",
                id, entity.getApplicationName(), entity.getSupportInfoUrl());

        return supportClient.testConnection(entity.getSupportInfoUrl());
    }
}
