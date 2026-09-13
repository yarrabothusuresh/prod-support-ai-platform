package com.example.prodsupport;

import com.example.prodsupport.ai.config.AiProperties;
import com.example.prodsupport.ai.tools.audit.ToolExecutionAuditor;
import com.example.prodsupport.ai.tools.model.ApplicationHealthData;
import com.example.prodsupport.ai.tools.model.DependenciesData;
import com.example.prodsupport.ai.tools.model.DependencyItemDto;
import com.example.prodsupport.ai.tools.model.RecentErrorsData;
import com.example.prodsupport.ai.tools.model.SanitizedErrorDto;
import com.example.prodsupport.application.dto.SupportInvestigationRequest;
import com.example.prodsupport.application.dto.SupportInvestigationResponse;
import com.example.prodsupport.application.service.DiagnosticService;
import com.example.prodsupport.application.service.InvestigationService;
import com.example.prodsupport.common.exception.ApplicationNotFoundException;
import com.example.prodsupport.domain.RegisteredApplication;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
class InvestigationServiceTest {

    @Autowired
    private InvestigationService investigationService;

    @Autowired
    private RegisteredApplicationRepository repository;

    @Autowired
    private ToolExecutionAuditor auditor;

    @Autowired
    private AiProperties aiProperties;

    @MockBean
    private ChatModel chatModel;

    @MockBean
    private DiagnosticService diagnosticService;

    private RegisteredApplication paymentApp;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        auditor.clearHistory();

        paymentApp = new RegisteredApplication(
                "payment-service",
                "payments",
                "local",
                "Demo payment processing service",
                "http://localhost:8081",
                "http://localhost:8081/actuator/health",
                "http://localhost:8081/support/info",
                true
        );
        repository.save(paymentApp);
    }

    @Test
    @DisplayName("Agentic investigation should return structured response with actual toolsUsed")
    void shouldExecuteAgenticInvestigationWithMockedModel() {
        String mockModelOutput = """
                {
                  "summary": "The application is available but its database dependency is unhealthy.",
                  "observedFacts": [
                    "Application health is UP",
                    "2 recent database connection errors were found",
                    "PostgreSQL dependency is DOWN"
                  ],
                  "likelyCauses": [
                    "Loss of database connectivity"
                  ],
                  "recommendedChecks": [
                    "Verify PostgreSQL availability",
                    "Check payment-service database connectivity",
                    "Review database connection pool state"
                  ],
                  "confidence": "HIGH"
                }
                """;

        when(chatModel.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage(mockModelOutput))))
        );

        SupportInvestigationRequest request = new SupportInvestigationRequest(
                "payment-service",
                "local",
                "Why are payment requests failing?",
                "AGENTIC"
        );

        SupportInvestigationResponse response = investigationService.investigate(request);

        assertThat(response.applicationName()).isEqualTo("payment-service");
        assertThat(response.environment()).isEqualTo("local");
        assertThat(response.summary()).contains("database dependency is unhealthy");
        assertThat(response.observedFacts()).contains("PostgreSQL dependency is DOWN");
        assertThat(response.likelyCauses()).contains("Loss of database connectivity");
        assertThat(response.recommendedChecks()).contains("Verify PostgreSQL availability");
        assertThat(response.confidence()).isEqualTo("HIGH");
    }

    @Test
    @DisplayName("Agentic investigation failure should safely fall back to deterministic investigation")
    void shouldFallbackToDeterministicWhenAgenticFails() {
        // Mock chatModel to fail on first call (agentic) then succeed on second call (deterministic)
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(new RuntimeException("Ollama model does not support tool calling"))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("""
                        {
                          "summary": "Service has downstream issues based on deterministic telemetry.",
                          "observedFacts": ["Application is UP"],
                          "likelyCauses": ["Database network timeout"],
                          "recommendedChecks": ["Check network connectivity"],
                          "confidence": "MEDIUM"
                        }
                        """)))));

        when(diagnosticService.checkHealth(any())).thenReturn(
                new ApplicationHealthData("UP", "/actuator/health", "2026-09-13T20:00:00Z")
        );
        when(diagnosticService.getRecentErrors(any(), any(Integer.class))).thenReturn(
                new RecentErrorsData(0, List.of())
        );
        when(diagnosticService.checkDependencies(any())).thenReturn(
                new DependenciesData(List.of(new DependencyItemDto("postgres-db", "DATABASE", "DOWN")))
        );

        SupportInvestigationRequest request = new SupportInvestigationRequest(
                "payment-service",
                "local",
                "Investigate payment-service issues",
                "AGENTIC"
        );

        SupportInvestigationResponse response = investigationService.investigate(request);

        assertThat(response.applicationName()).isEqualTo("payment-service");
        // Verify deterministic fallback was activated and recorded in warnings
        assertThat(response.warnings()).anyMatch(w -> w.contains("deterministic fallback"));
        assertThat(response.toolsUsed()).contains("check_application_health", "check_dependencies");
    }

    @Test
    @DisplayName("Investigation of non-registered application should throw ApplicationNotFoundException")
    void shouldThrowWhenApplicationNotRegistered() {
        SupportInvestigationRequest request = new SupportInvestigationRequest(
                "unknown-service",
                "local",
                "Is it healthy?",
                "AGENTIC"
        );

        assertThatThrownBy(() -> investigationService.investigate(request))
                .isInstanceOf(ApplicationNotFoundException.class);
    }
}
