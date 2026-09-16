package com.example.prodsupport;

import com.example.prodsupport.application.dto.SupportInvestigationRequest;
import com.example.prodsupport.application.dto.SupportInvestigationResponse;
import com.example.prodsupport.application.dto.kafka.ApplicationKafkaConfigRequest;
import com.example.prodsupport.application.service.DiagnosticService;
import com.example.prodsupport.application.service.InvestigationService;
import com.example.prodsupport.application.service.kafka.ApplicationKafkaConfigService;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.infrastructure.kafka.KafkaDiagnosticClient;
import com.example.prodsupport.infrastructure.kafka.model.KafkaClusterDiagnosticData;
import com.example.prodsupport.infrastructure.kafka.model.KafkaConsumerLagData;
import com.example.prodsupport.infrastructure.kafka.model.PartitionLagData;
import com.example.prodsupport.infrastructure.repository.ApplicationKafkaConfigRepository;
import com.example.prodsupport.infrastructure.repository.RegisteredApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest
class KafkaInvestigationTest {

    @Autowired
    private InvestigationService investigationService;

    @Autowired
    private RegisteredApplicationRepository applicationRepository;

    @Autowired
    private ApplicationKafkaConfigRepository kafkaConfigRepository;

    @Autowired
    private ApplicationKafkaConfigService configService;

    @MockBean
    private KafkaDiagnosticClient mockKafkaClient;

    @MockBean
    private DiagnosticService diagnosticService;

    private RegisteredApplication paymentApp;

    @BeforeEach
    void setUp() {
        kafkaConfigRepository.deleteAll();
        applicationRepository.deleteAll();

        paymentApp = new RegisteredApplication(
                "payment-service",
                "payments",
                "local",
                "Payment service",
                "http://localhost:8081",
                "http://localhost:8081/actuator/health",
                "http://localhost:8081/support/info",
                true
        );
        paymentApp = applicationRepository.save(paymentApp);

        configService.updateKafkaConfig(paymentApp.getId(), new ApplicationKafkaConfigRequest(
                true,
                List.of("localhost:9092"),
                List.of("payment-processing-group"),
                List.of("payment-events")
        ));
    }

    @Test
    @DisplayName("Should include Kafka facts in deterministic investigation when Kafka is configured")
    void shouldIncludeKafkaInDeterministicInvestigation() {
        when(mockKafkaClient.checkCluster(any(), any()))
                .thenReturn(new KafkaClusterDiagnosticData(true, "cluster-123", 1, List.of("localhost:9092"), null));

        when(mockKafkaClient.calculateConsumerLag(any(), eq("payment-processing-group"), any(), anyLong(), anyLong()))
                .thenReturn(new KafkaConsumerLagData(
                        "payment-processing-group",
                        "STABLE",
                        1,
                        5000L,
                        2500L,
                        KafkaConsumerLagData.LAG_CRITICAL,
                        List.of(
                                new PartitionLagData("payment-events", 0, 1, 3000L, 500L, 2500L, PartitionLagData.STATUS_OK),
                                new PartitionLagData("payment-events", 1, 1, 3000L, 500L, 2500L, PartitionLagData.STATUS_OK)
                        ),
                        List.of()
                ));

        SupportInvestigationRequest request = new SupportInvestigationRequest(
                "payment-service", "local", "Why is payment processing delayed?", "DETERMINISTIC"
        );

        SupportInvestigationResponse response = investigationService.investigate(request);

        assertThat(response).isNotNull();
        assertThat(response.observedFacts()).anyMatch(f -> f.contains("Kafka cluster is reachable"));
        assertThat(response.observedFacts()).anyMatch(f -> f.contains("lag is 5000") && f.contains("CRITICAL"));
        assertThat(response.toolsUsed()).contains("check_kafka_cluster", "check_kafka_consumer_lag");
    }

    @Test
    @DisplayName("Partial failure resilience: When Kafka fails, investigation continues with other diagnostics")
    void shouldContinueInvestigationWhenKafkaTimesOut() {
        when(mockKafkaClient.checkCluster(any(), any()))
                .thenReturn(KafkaClusterDiagnosticData.unreachable("Timed out waiting for Kafka"));

        when(mockKafkaClient.calculateConsumerLag(any(), eq("payment-processing-group"), any(), anyLong(), anyLong()))
                .thenReturn(new KafkaConsumerLagData(
                        "payment-processing-group",
                        "UNKNOWN",
                        0,
                        null,
                        null,
                        KafkaConsumerLagData.LAG_UNKNOWN,
                        List.of(),
                        List.of("Consumer lag check timed out")
                ));

        SupportInvestigationRequest request = new SupportInvestigationRequest(
                "payment-service", "local", "Is payment service healthy?", "DETERMINISTIC"
        );

        SupportInvestigationResponse response = investigationService.investigate(request);

        assertThat(response).isNotNull();
        assertThat(response.applicationName()).isEqualTo("payment-service");
        // Should contain warning about Kafka timeout without throwing unhandled exception
        assertThat(response.warnings()).anyMatch(w -> w.contains("Kafka") || w.contains("timed out"));
    }
}
