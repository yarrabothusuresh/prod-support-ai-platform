package com.example.prodsupport.logging;

import com.example.prodsupport.application.dto.SupportInvestigationRequest;
import com.example.prodsupport.application.dto.SupportInvestigationResponse;
import com.example.prodsupport.application.service.InvestigationService;
import com.example.prodsupport.database.model.*;
import com.example.prodsupport.database.service.ApplicationDatabaseDiagnosticService;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.infrastructure.repository.RegisteredApplicationRepository;
import com.example.prodsupport.logging.client.LogSearchClient;
import com.example.prodsupport.logging.model.*;
import com.example.prodsupport.logging.service.ErrorPatternService;
import com.example.prodsupport.logging.service.LogSearchService;
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
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class CrossSystemLogKafkaDatabaseInvestigationTest {

    @Autowired
    private InvestigationService investigationService;

    @Autowired
    private RegisteredApplicationRepository applicationRepository;

    @MockBean
    private ChatModel chatModel;

    @MockBean
    private ApplicationDatabaseDiagnosticService databaseDiagnosticService;

    @MockBean
    private LogSearchClient logSearchClient;

    @MockBean
    private LogSearchService logSearchService;

    @MockBean
    private ErrorPatternService errorPatternService;

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
    @DisplayName("Cross-system investigation correlating application health, logs, Kafka lag, and database pool pressure")
    void testCrossSystemLogsKafkaDatabaseInvestigation() {
        String mockLlmResponse = """
                {
                  "summary": "Payment processing is delayed. Evidence indicates database pool exhaustion causing 20 connection timeouts and Kafka consumer lag accumulation.",
                  "observedFacts": [
                    "20 DatabaseConnectionException errors recorded in Elasticsearch logs",
                    "Database connection pool is 100% utilized (10/10 active)",
                    "6 threads awaiting database connections"
                  ],
                  "likelyCauses": [
                    "Database connection timeouts are blocking payment processing threads, leading to consumer lag"
                  ],
                  "recommendedChecks": [
                    "Review recent connection timeout errors in Elasticsearch",
                    "Check database active queries and session duration",
                    "Follow Database Connection Pool Runbook"
                  ],
                  "knowledgeGuidance": [
                    "Follow approved runbook procedures before increasing pool size"
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
                        DatabaseHealthResult.up("payment-db", DatabaseType.POSTGRESQL, 12),
                        new ConnectionPoolResult("PaymentHikariPool", 10, 0, 10, 10, 2, 6, 100, "CRITICAL",
                                "Pool exhausted", "application-endpoint", Instant.now()),
                        DatabaseActivityResult.empty(),
                        List.of()
                )
        );

        when(logSearchClient.isAvailable()).thenReturn(true);

        LogEntryDto entry = new LogEntryDto(
                Instant.now().toString(), "ERROR", "DatabaseConnectionException",
                "Unable to acquire database connection", "CORR-001", "database", "Dao", null
        );
        when(logSearchService.searchLogs(eq("payment-service"), eq("local"), any(), any(), any(), any(), any(), any()))
                .thenReturn(new LogSearchResult("payment-service", "local", 20, false, List.of(entry), List.of()));

        when(errorPatternService.summarizeErrors(eq("payment-service"), eq("local"), anyInt(), anyInt()))
                .thenReturn(new ErrorPatternResult("payment-service", "local", 15,
                        List.of(new ErrorPatternDto("DatabaseConnectionException", 20L)), List.of()));

        SupportInvestigationRequest request = new SupportInvestigationRequest(
                "payment-service",
                "local",
                "Payment processing is delayed. Check logs, Kafka, database and runbooks.",
                "DETERMINISTIC"
        );

        SupportInvestigationResponse response = investigationService.investigate(request);

        assertThat(response.applicationName()).isEqualTo("payment-service");
        assertThat(response.summary()).contains("database");
        assertThat(response.toolsUsed()).contains(
                "check_database_health",
                "check_database_connection_pool",
                "search_application_errors",
                "get_error_pattern_summary"
        );
        assertThat(response.observedFacts()).anyMatch(f -> f.contains("DatabaseConnectionException") || f.contains("Elasticsearch") || f.contains("Centralized logs"));
    }
}

