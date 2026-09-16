package com.example.prodsupport;

import com.example.prodsupport.ai.tools.*;
import com.example.prodsupport.ai.tools.audit.ToolExecutionAuditor;
import com.example.prodsupport.ai.tools.context.InvestigationContext;
import com.example.prodsupport.ai.tools.context.InvestigationContextHolder;
import com.example.prodsupport.ai.tools.model.*;
import com.example.prodsupport.ai.tools.security.ToolAllowlist;
import com.example.prodsupport.application.dto.kafka.ApplicationKafkaConfigRequest;
import com.example.prodsupport.application.service.kafka.ApplicationKafkaConfigService;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.infrastructure.kafka.KafkaDiagnosticClient;
import com.example.prodsupport.infrastructure.kafka.model.KafkaClusterDiagnosticData;
import com.example.prodsupport.infrastructure.kafka.model.KafkaConsumerGroupData;
import com.example.prodsupport.infrastructure.kafka.model.KafkaConsumerLagData;
import com.example.prodsupport.infrastructure.kafka.model.KafkaTopicData;
import com.example.prodsupport.infrastructure.repository.ApplicationKafkaConfigRepository;
import com.example.prodsupport.infrastructure.repository.RegisteredApplicationRepository;
import org.junit.jupiter.api.AfterEach;
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
class KafkaAiToolsTest {

    @Autowired
    private KafkaClusterAiTool clusterTool;

    @Autowired
    private KafkaConsumerGroupAiTool consumerGroupTool;

    @Autowired
    private KafkaConsumerLagAiTool consumerLagTool;

    @Autowired
    private KafkaTopicAiTool topicTool;

    @Autowired
    private ToolExecutionAuditor auditor;

    @Autowired
    private RegisteredApplicationRepository applicationRepository;

    @Autowired
    private ApplicationKafkaConfigRepository kafkaConfigRepository;

    @Autowired
    private ApplicationKafkaConfigService configService;

    @MockBean
    private KafkaDiagnosticClient mockKafkaClient;

    private RegisteredApplication paymentApp;
    private RegisteredApplication disabledApp;

    @BeforeEach
    void setUp() {
        auditor.clearHistory();
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

        disabledApp = new RegisteredApplication(
                "legacy-service",
                "legacy",
                "local",
                "Disabled service",
                "http://localhost:8089",
                "http://localhost:8089/actuator/health",
                "http://localhost:8089/support/info",
                false
        );
        disabledApp = applicationRepository.save(disabledApp);

        // Configure Kafka for paymentApp
        configService.updateKafkaConfig(paymentApp.getId(), new ApplicationKafkaConfigRequest(
                true,
                List.of("localhost:9092"),
                List.of("payment-processing-group"),
                List.of("payment-events")
        ));
    }

    @AfterEach
    void tearDown() {
        InvestigationContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should execute check_kafka_cluster and record evidence and audit")
    void shouldExecuteKafkaClusterTool() {
        when(mockKafkaClient.checkCluster(any(), any()))
                .thenReturn(new KafkaClusterDiagnosticData(true, "test-cluster-id", 3, List.of("broker1:9092"), null));

        InvestigationContext context = new InvestigationContext("payment-service", "local", "Is Kafka cluster healthy?", 6);
        InvestigationContextHolder.setContext(context);

        KafkaClusterRequest request = new KafkaClusterRequest("payment-service", "local");
        ToolExecutionResult<KafkaClusterDiagnosticData> result = clusterTool.apply(request);

        assertThat(result.success()).isTrue();
        assertThat(result.toolName()).isEqualTo(ToolAllowlist.TOOL_CHECK_KAFKA_CLUSTER);
        assertThat(result.data().clusterId()).isEqualTo("test-cluster-id");
        assertThat(result.data().brokerCount()).isEqualTo(3);

        // Verify context
        assertThat(context.getToolsUsed()).contains(ToolAllowlist.TOOL_CHECK_KAFKA_CLUSTER);
        assertThat(context.getEvidence()).anyMatch(e -> e.contains("Kafka cluster is reachable"));

        // Verify audit
        assertThat(auditor.getAuditHistory()).hasSize(1);
        assertThat(auditor.getAuditHistory().get(0).toolName()).isEqualTo(ToolAllowlist.TOOL_CHECK_KAFKA_CLUSTER);
        assertThat(auditor.getAuditHistory().get(0).success()).isTrue();
    }

    @Test
    @DisplayName("Should execute check_kafka_consumer_lag for configured group")
    void shouldExecuteKafkaConsumerLagTool() {
        when(mockKafkaClient.calculateConsumerLag(any(), eq("payment-processing-group"), any(), anyLong(), anyLong()))
                .thenReturn(new KafkaConsumerLagData(
                        "payment-processing-group",
                        "STABLE",
                        1,
                        250L,
                        250L,
                        KafkaConsumerLagData.LAG_WARNING,
                        List.of(),
                        List.of()
                ));

        InvestigationContext context = new InvestigationContext("payment-service", "local", "Check consumer lag", 6);
        InvestigationContextHolder.setContext(context);

        KafkaConsumerLagRequest request = new KafkaConsumerLagRequest("payment-service", "local", "payment-processing-group");
        ToolExecutionResult<KafkaConsumerLagData> result = consumerLagTool.apply(request);

        assertThat(result.success()).isTrue();
        assertThat(result.data().totalLag()).isEqualTo(250L);
        assertThat(result.data().lagStatus()).isEqualTo(KafkaConsumerLagData.LAG_WARNING);

        assertThat(context.getToolsUsed()).contains(ToolAllowlist.TOOL_CHECK_KAFKA_CONSUMER_LAG);
        assertThat(context.getEvidence()).anyMatch(e -> e.contains("total lag is 250"));
    }

    @Test
    @DisplayName("Security boundary: Should reject unconfigured consumer group")
    void shouldRejectUnconfiguredConsumerGroup() {
        KafkaConsumerLagRequest request = new KafkaConsumerLagRequest("payment-service", "local", "unauthorized-foreign-group");
        ToolExecutionResult<KafkaConsumerLagData> result = consumerLagTool.apply(request);

        assertThat(result.success()).isFalse();
        assertThat(result.warning()).contains("not configured or permitted");

        // Verify audit logged failure
        assertThat(auditor.getAuditHistory()).hasSize(1);
        assertThat(auditor.getAuditHistory().get(0).success()).isFalse();
    }


    @Test
    @DisplayName("Security boundary: Should reject unconfigured topic")
    void shouldRejectUnconfiguredTopic() {
        KafkaTopicRequest request = new KafkaTopicRequest("payment-service", "local", "arbitrary-secret-topic");
        ToolExecutionResult<KafkaTopicData> result = topicTool.apply(request);

        assertThat(result.success()).isFalse();
        assertThat(result.warning()).contains("not configured or permitted");
    }

    @Test
    @DisplayName("Should reject execution when application is disabled")
    void shouldRejectDisabledApplication() {
        KafkaClusterRequest request = new KafkaClusterRequest("legacy-service", "local");
        ToolExecutionResult<KafkaClusterDiagnosticData> result = clusterTool.apply(request);

        assertThat(result.success()).isFalse();
        assertThat(result.warning()).contains("disabled");
    }

    @Test
    @DisplayName("Should handle Kafka cluster unavailable cleanly without crashing")
    void shouldHandleKafkaClusterUnavailableCleanly() {
        when(mockKafkaClient.checkCluster(any(), any()))
                .thenReturn(KafkaClusterDiagnosticData.unreachable("Connection timed out after 5000ms"));

        InvestigationContext context = new InvestigationContext("payment-service", "local", "Check Kafka", 6);
        InvestigationContextHolder.setContext(context);

        KafkaClusterRequest request = new KafkaClusterRequest("payment-service", "local");
        ToolExecutionResult<KafkaClusterDiagnosticData> result = clusterTool.apply(request);

        assertThat(result.success()).isFalse();
        assertThat(result.warning()).contains("Connection timed out");
        assertThat(context.getEvidence()).anyMatch(e -> e.contains("unreachable"));
    }
}
