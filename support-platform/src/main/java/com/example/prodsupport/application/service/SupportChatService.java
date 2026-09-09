package com.example.prodsupport.application.service;

import com.example.prodsupport.ai.client.SupportAiClient;
import com.example.prodsupport.ai.model.ApplicationSupportContext;
import com.example.prodsupport.ai.model.SupportAiResult;
import com.example.prodsupport.ai.service.ApplicationContextService;
import com.example.prodsupport.application.dto.SupportChatRequest;
import com.example.prodsupport.application.dto.SupportChatResponse;
import com.example.prodsupport.common.exception.ApplicationNotFoundException;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.infrastructure.repository.RegisteredApplicationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class SupportChatService {

    private static final Logger log = LoggerFactory.getLogger(SupportChatService.class);

    private final RegisteredApplicationRepository repository;
    private final ApplicationContextService contextService;
    private final SupportAiClient aiClient;

    public SupportChatService(RegisteredApplicationRepository repository,
                              ApplicationContextService contextService,
                              SupportAiClient aiClient) {
        this.repository = repository;
        this.contextService = contextService;
        this.aiClient = aiClient;
    }

    public SupportChatResponse chat(SupportChatRequest request) {
        String appName = request.applicationName().trim();
        String environment = request.environment().trim();
        String question = request.question().trim();

        log.info("Processing support chat request for application: '{}/{}'", appName, environment);

        // 1. Resolve application from registry
        RegisteredApplication app = repository.findByApplicationNameAndEnvironment(appName, environment)
                .orElseThrow(() -> {
                    log.warn("Application not found in registry: '{}/{}'", appName, environment);
                    return new ApplicationNotFoundException(appName, environment);
                });

        // 2. Collect live application context
        ApplicationSupportContext context = contextService.collectContext(app);

        // 3. Send grounded prompt to AI model
        SupportAiResult aiResult = aiClient.analyze(context, question);

        // 4. Combine warnings from context collection and AI parsing
        Set<String> allWarnings = new LinkedHashSet<>();
        if (context.warnings() != null) {
            allWarnings.addAll(context.warnings());
        }
        if (aiResult.warnings() != null) {
            allWarnings.addAll(aiResult.warnings());
        }

        log.info("Support chat completed successfully for '{}/{}' with confidence={}",
                appName, environment, aiResult.confidence());

        return new SupportChatResponse(
                appName,
                environment,
                aiResult.summary(),
                aiResult.observedFacts(),
                aiResult.possibleCauses(),
                aiResult.recommendedChecks(),
                aiResult.confidence(),
                new ArrayList<>(allWarnings)
        );
    }
}
