package com.example.prodsupport;

import com.example.prodsupport.application.dto.SupportInvestigationRequest;
import com.example.prodsupport.application.dto.SupportInvestigationResponse;
import com.example.prodsupport.application.service.DiagnosticService;
import com.example.prodsupport.application.service.InvestigationService;
import com.example.prodsupport.database.model.*;
import com.example.prodsupport.database.service.ApplicationDatabaseDiagnosticService;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.infrastructure.kafka.model.KafkaConsumerGroupData;
import com.example.prodsupport.infrastructure.kafka.model.KafkaConsumerLagData;
import com.example.prodsupport.infrastructure.repository.RegisteredApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
class CrossSystemKafkaAndDatabaseInvestigationTest {

    @Autowired
    private InvestigationService investigationService;

    @Autowired
    private RegisteredApplicationRepository applicationRepository;

    @MockBean
    private ChatModel chatModel;

    @MockBean
    private ApplicationDatabaseDiagnosticService databaseDiagnosticService;

    private RegisteredApplication paymentApp;

    @BeforeEach
    void setUp() {
        applicationRepository.deleteAll();
        paymentApp = new RegisteredApplication(
                "payment-service",
                "payments",
                "local",
                "Demo payment service",
                "http://localhost:8081",
                "http://localhost:8081/actuator/health",
                "http://localhost:8081/support/info",
                true
        );
        paymentApp = applicationRepository.save(paymentApp);
    }

    @Test
    @DisplayName("Correlate Kafka lag with database connection pool pressure in a single investigation")
    void testCrossSystemKafkaAndDatabaseCorrelation() {
        // Mock LLM response representing grounded correlation between Kafka and DB pool pressure
        String mockLlmResponse = """
                {
                  "summary": "Payment processing is delayed due to severe database connection pool pressure slowing down Kafka consumers.",
                  "observedFacts": [
                    "Kafka consumer group 'payment-processing-group' lag is 5000",
                    "Database connection pool is 100% utilized (10/10 active)",
                    "4 application threads are awaiting database connections",
                    "Database connectivity is UP"
                  ],
                  "likelyCauses": [
                    "Consumer threads are blocked awaiting database connections, causing Kafka lag to accumulate"
                  ],
                  "recommendedChecks": [
                    "Check long-running database activity",
                    "Monitor whether Kafka lag decreases once database pool pressure is resolved",
                    "Review Payment Service Database Connection Pool Runbook"
                  ],
                  "knowledgeGuidance": [
                    "Follow the payment-service-database-pool runbook before adjusting pool size"
                  ],
                  "confidence": "HIGH"
                }
                """;

        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(mockLlmResponse)))));

        when(databaseDiagnosticService.runDiagnostics(any())).thenReturn(
                new ApplicationDatabaseDiagnostics(
                        true,
                        "payment-db",
                        DatabaseType.POSTGRESQL,
                        DatabaseHealthResult.up("payment-db", DatabaseType.POSTGRESQL, 15),
                        new ConnectionPoolResult("PaymentHikariPool", 10, 0, 10, 10, 2, 4, 100, "CRITICAL",
                                "Pool exhausted", "application-endpoint", Instant.now()),
                        DatabaseActivityResult.empty(),
                        List.of()
                )
        );

        SupportInvestigationRequest request = new SupportInvestigationRequest(
                "payment-service",
                "local",
                "Kafka lag is high. Could the database be slowing the consumer?",
                "DETERMINISTIC"
        );

        SupportInvestigationResponse response = investigationService.investigate(request);

        assertThat(response.applicationName()).isEqualTo("payment-service");
        assertThat(response.summary()).contains("database connection pool pressure");
        assertThat(response.toolsUsed()).contains("check_database_health", "check_database_connection_pool");
        assertThat(response.observedFacts()).anyMatch(f -> f.contains("PaymentHikariPool") || f.contains("100%"));
    }
}
