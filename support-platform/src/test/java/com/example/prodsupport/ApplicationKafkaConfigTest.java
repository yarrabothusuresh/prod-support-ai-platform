package com.example.prodsupport;

import com.example.prodsupport.application.dto.kafka.ApplicationKafkaConfigRequest;
import com.example.prodsupport.application.dto.kafka.ApplicationKafkaConfigResponse;
import com.example.prodsupport.application.service.kafka.ApplicationKafkaConfigService;
import com.example.prodsupport.common.exception.ApplicationNotFoundException;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.infrastructure.repository.ApplicationKafkaConfigRepository;
import com.example.prodsupport.infrastructure.repository.RegisteredApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ApplicationKafkaConfigTest {

    @Autowired
    private ApplicationKafkaConfigService configService;

    @Autowired
    private ApplicationKafkaConfigRepository kafkaConfigRepository;

    @Autowired
    private RegisteredApplicationRepository applicationRepository;

    private RegisteredApplication app;

    @BeforeEach
    void setUp() {
        kafkaConfigRepository.deleteAll();
        applicationRepository.deleteAll();

        app = new RegisteredApplication(
                "payment-service",
                "payments",
                "local",
                "Payment service",
                "http://localhost:8081",
                "http://localhost:8081/actuator/health",
                "http://localhost:8081/support/info",
                true
        );
        app = applicationRepository.save(app);
    }

    @Test
    @DisplayName("Should create and retrieve Kafka configuration for registered application")
    void shouldCreateAndRetrieveKafkaConfig() {
        ApplicationKafkaConfigRequest request = new ApplicationKafkaConfigRequest(
                true,
                List.of("localhost:9092"),
                List.of("payment-processing-group"),
                List.of("payment-events")
        );

        ApplicationKafkaConfigResponse saved = configService.updateKafkaConfig(app.getId(), request);

        assertThat(saved).isNotNull();
        assertThat(saved.applicationName()).isEqualTo("payment-service");
        assertThat(saved.bootstrapServers()).containsExactly("localhost:9092");
        assertThat(saved.consumerGroups()).containsExactly("payment-processing-group");
        assertThat(saved.topics()).containsExactly("payment-events");
        assertThat(saved.enabled()).isTrue();

        Optional<ApplicationKafkaConfigResponse> fetched = configService.getKafkaConfig(app.getId());
        assertThat(fetched).isPresent();
        assertThat(fetched.get().consumerGroups()).contains("payment-processing-group");
    }

    @Test
    @DisplayName("Should deduplicate repeated consumer groups and topics")
    void shouldDeduplicateConsumerGroupsAndTopics() {
        ApplicationKafkaConfigRequest request = new ApplicationKafkaConfigRequest(
                true,
                List.of("localhost:9092"),
                List.of("payment-processing-group", "payment-processing-group", "payment-notifications-group"),
                List.of("payment-events", "payment-events")
        );

        ApplicationKafkaConfigResponse saved = configService.updateKafkaConfig(app.getId(), request);

        assertThat(saved.consumerGroups()).hasSize(2);
        assertThat(saved.consumerGroups()).containsExactlyInAnyOrder("payment-processing-group", "payment-notifications-group");
        assertThat(saved.topics()).hasSize(1);
        assertThat(saved.topics()).containsExactly("payment-events");
    }

    @Test
    @DisplayName("Should reject empty bootstrap servers")
    void shouldRejectEmptyBootstrapServers() {
        ApplicationKafkaConfigRequest request = new ApplicationKafkaConfigRequest(
                true,
                List.of(),
                List.of("payment-processing-group"),
                List.of("payment-events")
        );

        assertThatThrownBy(() -> configService.updateKafkaConfig(app.getId(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bootstrapServers");
    }

    @Test
    @DisplayName("Should reject Kafka configuration for unknown application ID")
    void shouldRejectUnknownApplication() {
        ApplicationKafkaConfigRequest request = new ApplicationKafkaConfigRequest(
                true,
                List.of("localhost:9092"),
                List.of("group-1"),
                List.of("topic-1")
        );

        assertThatThrownBy(() -> configService.updateKafkaConfig(99999L, request))
                .isInstanceOf(ApplicationNotFoundException.class);
    }
}
