package com.example.prodsupport.application.service.kafka;

import com.example.prodsupport.application.dto.kafka.KafkaApplicationDiagnosticSummary;
import com.example.prodsupport.application.dto.kafka.KafkaConsumerLagSummaryDto;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.domain.kafka.ApplicationKafkaConfig;
import com.example.prodsupport.infrastructure.kafka.KafkaDiagnosticClient;
import com.example.prodsupport.infrastructure.kafka.KafkaDiagnosticProperties;
import com.example.prodsupport.infrastructure.kafka.model.KafkaClusterDiagnosticData;
import com.example.prodsupport.infrastructure.kafka.model.KafkaConsumerGroupData;
import com.example.prodsupport.infrastructure.kafka.model.KafkaConsumerLagData;
import com.example.prodsupport.infrastructure.kafka.model.KafkaTopicData;
import com.example.prodsupport.infrastructure.repository.ApplicationKafkaConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class KafkaDiagnosticService {

    private static final Logger log = LoggerFactory.getLogger(KafkaDiagnosticService.class);

    private final ApplicationKafkaConfigRepository kafkaConfigRepository;
    private final KafkaDiagnosticClient kafkaDiagnosticClient;
    private final KafkaDiagnosticProperties properties;

    public KafkaDiagnosticService(ApplicationKafkaConfigRepository kafkaConfigRepository,
                                  KafkaDiagnosticClient kafkaDiagnosticClient,
                                  KafkaDiagnosticProperties properties) {
        this.kafkaConfigRepository = kafkaConfigRepository;
        this.kafkaDiagnosticClient = kafkaDiagnosticClient;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public Optional<ApplicationKafkaConfig> getKafkaConfig(RegisteredApplication app) {
        return kafkaConfigRepository.findByApplicationId(app.getId());
    }

    @Transactional(readOnly = true)
    public boolean isKafkaConfiguredAndEnabled(RegisteredApplication app) {
        if (!properties.isEnabled()) {
            return false;
        }
        return kafkaConfigRepository.findByApplicationId(app.getId())
                .map(ApplicationKafkaConfig::isEnabled)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public KafkaClusterDiagnosticData checkCluster(RegisteredApplication app) {
        ApplicationKafkaConfig config = requireEnabledKafkaConfig(app);
        Duration timeout = properties.getRequestTimeout();
        return kafkaDiagnosticClient.checkCluster(config.getBootstrapServerList(), timeout);
    }

    @Transactional(readOnly = true)
    public KafkaConsumerGroupData checkConsumerGroup(RegisteredApplication app, String consumerGroup) {
        if (consumerGroup == null || consumerGroup.trim().isBlank()) {
            throw new IllegalArgumentException("consumerGroup parameter must not be blank");
        }
        String normalizedGroup = consumerGroup.trim();
        ApplicationKafkaConfig config = requireEnabledKafkaConfig(app);

        validateConsumerGroupAllowed(config, app, normalizedGroup);

        Duration timeout = properties.getRequestTimeout();
        return kafkaDiagnosticClient.getConsumerGroupInfo(config.getBootstrapServerList(), normalizedGroup, timeout);
    }

    @Transactional(readOnly = true)
    public KafkaConsumerLagData checkConsumerLag(RegisteredApplication app, String consumerGroup) {
        if (consumerGroup == null || consumerGroup.trim().isBlank()) {
            throw new IllegalArgumentException("consumerGroup parameter must not be blank");
        }
        String normalizedGroup = consumerGroup.trim();
        ApplicationKafkaConfig config = requireEnabledKafkaConfig(app);

        validateConsumerGroupAllowed(config, app, normalizedGroup);

        Duration timeout = properties.getRequestTimeout();
        long warningThreshold = properties.getLag().getWarningThreshold();
        long criticalThreshold = properties.getLag().getCriticalThreshold();

        return kafkaDiagnosticClient.calculateConsumerLag(
                config.getBootstrapServerList(),
                normalizedGroup,
                timeout,
                warningThreshold,
                criticalThreshold
        );
    }

    @Transactional(readOnly = true)
    public KafkaTopicData checkTopic(RegisteredApplication app, String topic) {
        if (topic == null || topic.trim().isBlank()) {
            throw new IllegalArgumentException("topic parameter must not be blank");
        }
        String normalizedTopic = topic.trim();
        ApplicationKafkaConfig config = requireEnabledKafkaConfig(app);

        validateTopicAllowed(config, app, normalizedTopic);

        Duration timeout = properties.getRequestTimeout();
        return kafkaDiagnosticClient.getTopicMetadata(config.getBootstrapServerList(), normalizedTopic, timeout);
    }

    @Transactional(readOnly = true)
    public KafkaApplicationDiagnosticSummary getDiagnosticSummary(RegisteredApplication app) {
        Optional<ApplicationKafkaConfig> configOpt = kafkaConfigRepository.findByApplicationId(app.getId());
        if (configOpt.isEmpty() || !configOpt.get().isEnabled()) {
            return KafkaApplicationDiagnosticSummary.disabled(
                    "Kafka diagnostics not configured or disabled for application '" + app.getApplicationName() + "'"
            );
        }

        ApplicationKafkaConfig config = configOpt.get();
        Duration timeout = properties.getRequestTimeout();
        List<String> bootstrapServers = config.getBootstrapServerList();
        List<String> warnings = new ArrayList<>();

        // 1. Check cluster reachability
        KafkaClusterDiagnosticData cluster = kafkaDiagnosticClient.checkCluster(bootstrapServers, timeout);
        if (!cluster.reachable()) {
            warnings.add(cluster.warning() != null ? cluster.warning() : "Kafka cluster is unreachable");
        }

        // 2. Check lag for each configured consumer group
        List<KafkaConsumerLagSummaryDto> groupSummaries = new ArrayList<>();
        long warningThreshold = properties.getLag().getWarningThreshold();
        long criticalThreshold = properties.getLag().getCriticalThreshold();

        for (String group : config.getConsumerGroupNames()) {
            try {
                KafkaConsumerLagData lagData = kafkaDiagnosticClient.calculateConsumerLag(
                        bootstrapServers, group, timeout, warningThreshold, criticalThreshold
                );
                groupSummaries.add(new KafkaConsumerLagSummaryDto(
                        group,
                        lagData.state(),
                        lagData.memberCount(),
                        lagData.totalLag(),
                        lagData.highestPartitionLag(),
                        lagData.lagStatus()
                ));
                if (lagData.warnings() != null) {
                    warnings.addAll(lagData.warnings());
                }
            } catch (Exception ex) {
                log.warn("Failed to collect lag summary for group '{}': {}", group, ex.getMessage());
                warnings.add("Consumer group '" + group + "' lag check failed: " + ex.getMessage());
                groupSummaries.add(new KafkaConsumerLagSummaryDto(
                        group, "UNKNOWN", 0, null, null, KafkaConsumerLagData.LAG_UNKNOWN
                ));
            }
        }

        return new KafkaApplicationDiagnosticSummary(
                true,
                cluster.reachable(),
                cluster.clusterId(),
                cluster.brokerCount(),
                groupSummaries,
                List.copyOf(config.getTopicNames()),
                warnings
        );
    }

    private ApplicationKafkaConfig requireEnabledKafkaConfig(RegisteredApplication app) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Kafka diagnostics are globally disabled in platform configuration");
        }
        ApplicationKafkaConfig config = kafkaConfigRepository.findByApplicationId(app.getId())
                .orElseThrow(() -> new IllegalStateException("Kafka diagnostics are not configured for application '" +
                        app.getApplicationName() + "' in environment '" + app.getEnvironment() + "'"));

        if (!config.isEnabled()) {
            throw new IllegalStateException("Kafka diagnostics are disabled for application '" +
                    app.getApplicationName() + "' in environment '" + app.getEnvironment() + "'");
        }
        return config;
    }

    private void validateConsumerGroupAllowed(ApplicationKafkaConfig config, RegisteredApplication app, String consumerGroup) {
        if (!config.hasConsumerGroup(consumerGroup)) {
            log.warn("Security boundary check failed: consumer group '{}' is not configured for application '{}/{}'",
                    consumerGroup, app.getApplicationName(), app.getEnvironment());
            throw new IllegalArgumentException("Consumer group '" + consumerGroup +
                    "' is not configured or permitted for application '" + app.getApplicationName() + "'");
        }
    }

    private void validateTopicAllowed(ApplicationKafkaConfig config, RegisteredApplication app, String topic) {
        if (!config.hasTopic(topic)) {
            log.warn("Security boundary check failed: topic '{}' is not configured for application '{}/{}'",
                    topic, app.getApplicationName(), app.getEnvironment());
            throw new IllegalArgumentException("Topic '" + topic +
                    "' is not configured or permitted for application '" + app.getApplicationName() + "'");
        }
    }
}
