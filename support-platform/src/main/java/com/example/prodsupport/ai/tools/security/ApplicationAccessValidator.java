package com.example.prodsupport.ai.tools.security;

import com.example.prodsupport.common.exception.ApplicationDisabledException;
import com.example.prodsupport.common.exception.ApplicationNotFoundException;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.infrastructure.repository.RegisteredApplicationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ApplicationAccessValidator {

    private static final Logger log = LoggerFactory.getLogger(ApplicationAccessValidator.class);

    private final RegisteredApplicationRepository repository;

    public ApplicationAccessValidator(RegisteredApplicationRepository repository) {
        this.repository = repository;
    }

    public RegisteredApplication validateAndGet(String applicationName, String environment) {
        if (applicationName == null || applicationName.trim().isEmpty()) {
            throw new IllegalArgumentException("applicationName must not be blank");
        }
        if (environment == null || environment.trim().isEmpty()) {
            throw new IllegalArgumentException("environment must not be blank");
        }

        String normalizedApp = applicationName.trim();
        String normalizedEnv = environment.trim();

        RegisteredApplication app = repository.findByApplicationNameAndEnvironment(normalizedApp, normalizedEnv)
                .orElseThrow(() -> {
                    log.warn("Access boundary check failed: application not found '{}/{}'", normalizedApp, normalizedEnv);
                    return new ApplicationNotFoundException(normalizedApp, normalizedEnv);
                });

        if (!app.isEnabled()) {
            log.warn("Access boundary check failed: application '{}/{}' is disabled", normalizedApp, normalizedEnv);
            throw new ApplicationDisabledException(normalizedApp, normalizedEnv);
        }

        return app;
    }
}
