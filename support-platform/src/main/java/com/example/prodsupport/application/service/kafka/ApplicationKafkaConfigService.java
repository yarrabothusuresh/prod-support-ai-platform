package com.example.prodsupport.application.service.kafka;

import com.example.prodsupport.application.dto.kafka.ApplicationKafkaConfigRequest;
import com.example.prodsupport.application.dto.kafka.ApplicationKafkaConfigResponse;
import com.example.prodsupport.common.exception.ApplicationNotFoundException;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.domain.kafka.ApplicationKafkaConfig;
import com.example.prodsupport.domain.kafka.ApplicationKafkaConsumerGroup;
import com.example.prodsupport.domain.kafka.ApplicationKafkaTopic;
import com.example.prodsupport.infrastructure.kafka.KafkaDiagnosticProperties;
import com.example.prodsupport.infrastructure.repository.ApplicationKafkaConfigRepository;
import com.example.prodsupport.infrastructure.repository.RegisteredApplicationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ApplicationKafkaConfigService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationKafkaConfigService.class);

    private final ApplicationKafkaConfigRepository kafkaConfigRepository;
    private final RegisteredApplicationRepository applicationRepository;
    private final KafkaDiagnosticProperties defaultProperties;

    public ApplicationKafkaConfigService(ApplicationKafkaConfigRepository kafkaConfigRepository,
                                         RegisteredApplicationRepository applicationRepository,
                                         KafkaDiagnosticProperties defaultProperties) {
        this.kafkaConfigRepository = kafkaConfigRepository;
        this.applicationRepository = applicationRepository;
        this.defaultProperties = defaultProperties;
    }

    @Transactional(readOnly = true)
    public Optional<ApplicationKafkaConfigResponse> getKafkaConfig(Long applicationId) {
        RegisteredApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ApplicationNotFoundException(applicationId));

        return kafkaConfigRepository.findByApplicationId(app.getId())
                .map(ApplicationKafkaConfigResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public Optional<ApplicationKafkaConfig> getKafkaConfigEntity(String applicationName, String environment) {
        return kafkaConfigRepository.findByApplicationApplicationNameAndApplicationEnvironment(
                applicationName.trim(), environment.trim());
    }

    @Transactional
    public ApplicationKafkaConfigResponse updateKafkaConfig(Long applicationId, ApplicationKafkaConfigRequest request) {
        RegisteredApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ApplicationNotFoundException(applicationId));

        if (request.bootstrapServers() == null || request.bootstrapServers().isEmpty()) {
            throw new IllegalArgumentException("bootstrapServers list must not be empty");
        }

        // Validate each bootstrap server format
        for (String server : request.bootstrapServers()) {
            if (server == null || server.trim().isBlank()) {
                throw new IllegalArgumentException("bootstrapServer entries must not be blank");
            }
        }

        String bootstrapServersJoined = request.bootstrapServers().stream()
                .map(String::trim)
                .collect(Collectors.joining(","));

        ApplicationKafkaConfig config = kafkaConfigRepository.findByApplicationId(app.getId())
                .orElseGet(() -> new ApplicationKafkaConfig(app, bootstrapServersJoined, true));

        config.setBootstrapServers(bootstrapServersJoined);
        config.setEnabled(request.enabled() != null ? request.enabled() : true);

        // Update consumer groups
        Set<String> distinctGroups = request.consumerGroups() == null ? Collections.emptySet() :
                request.consumerGroups().stream()
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toCollection(LinkedHashSet::new));

        // Clear existing and set new distinct consumer groups
        config.getConsumerGroups().clear();
        for (String group : distinctGroups) {
            config.getConsumerGroups().add(new ApplicationKafkaConsumerGroup(config, group, true));
        }

        // Update topics
        Set<String> distinctTopics = request.topics() == null ? Collections.emptySet() :
                request.topics().stream()
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toCollection(LinkedHashSet::new));

        config.getTopics().clear();
        for (String topic : distinctTopics) {
            config.getTopics().add(new ApplicationKafkaTopic(config, topic, true));
        }

        ApplicationKafkaConfig saved = kafkaConfigRepository.save(config);
        log.info("Configured Kafka diagnostics for application '{}' ({}/{}): {} consumer groups, {} topics",
                app.getId(), app.getApplicationName(), app.getEnvironment(),
                saved.getConsumerGroups().size(), saved.getTopics().size());

        return ApplicationKafkaConfigResponse.fromEntity(saved);
    }
}
