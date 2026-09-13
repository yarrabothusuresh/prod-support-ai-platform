package com.example.prodsupport;

import com.example.prodsupport.application.dto.SupportInvestigationRequest;
import com.example.prodsupport.application.dto.SupportInvestigationResponse;
import com.example.prodsupport.application.service.InvestigationService;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.infrastructure.repository.RegisteredApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Optional Ollama integration tests.
 * Only run when -Dollama.it.enabled=true is set (e.g. via profile -Pollama-it).
 */
@SpringBootTest
@Tag("ollama-it")
@EnabledIfSystemProperty(named = "ollama.it.enabled", matches = "true")
class OllamaToolCallingIT {

    @Autowired
    private InvestigationService investigationService;

    @Autowired
    private RegisteredApplicationRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        RegisteredApplication app = new RegisteredApplication(
                "payment-service",
                "payments",
                "local",
                "Demo payment service",
                "http://localhost:8081",
                "http://localhost:8081/actuator/health",
                "http://localhost:8081/support/info",
                true
        );
        repository.save(app);
    }

    @Test
    @DisplayName("IT: Health question should invoke check_application_health tool")
    void shouldSelectHealthToolForHealthQuestion() {
        SupportInvestigationRequest request = new SupportInvestigationRequest(
                "payment-service",
                "local",
                "Is payment-service currently healthy and running?",
                "AGENTIC"
        );

        SupportInvestigationResponse response = investigationService.investigate(request);

        assertThat(response).isNotNull();
        assertThat(response.summary()).isNotBlank();
        assertThat(response.toolsUsed()).isNotEmpty();
        assertThat(response.toolsUsed()).contains("check_application_health");
        assertThat(response.toolExecutionCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("IT: Error question should invoke get_recent_errors tool")
    void shouldSelectErrorToolForErrorQuestion() {
        SupportInvestigationRequest request = new SupportInvestigationRequest(
                "payment-service",
                "local",
                "What recent errors or exceptions occurred in payment-service?",
                "AGENTIC"
        );

        SupportInvestigationResponse response = investigationService.investigate(request);

        assertThat(response).isNotNull();
        assertThat(response.summary()).isNotBlank();
        assertThat(response.toolsUsed()).isNotEmpty();
        assertThat(response.toolsUsed()).contains("get_recent_errors");
    }

    @Test
    @DisplayName("IT: Dependency question should invoke check_dependencies tool")
    void shouldSelectDependencyToolForDependencyQuestion() {
        SupportInvestigationRequest request = new SupportInvestigationRequest(
                "payment-service",
                "local",
                "Are downstream dependencies or databases failing for payment-service?",
                "AGENTIC"
        );

        SupportInvestigationResponse response = investigationService.investigate(request);

        assertThat(response).isNotNull();
        assertThat(response.summary()).isNotBlank();
        assertThat(response.toolsUsed()).isNotEmpty();
        assertThat(response.toolsUsed()).contains("check_dependencies");
    }

    @Test
    @DisplayName("IT: Comprehensive question should trigger multiple tools")
    void shouldTriggerMultipleToolsForGeneralInvestigation() {
        SupportInvestigationRequest request = new SupportInvestigationRequest(
                "payment-service",
                "local",
                "Investigate why payment-service users are experiencing failures.",
                "AGENTIC"
        );

        SupportInvestigationResponse response = investigationService.investigate(request);

        assertThat(response).isNotNull();
        assertThat(response.summary()).isNotBlank();
        assertThat(response.toolsUsed()).isNotEmpty();
        assertThat(response.observedFacts()).isNotEmpty();
        assertThat(response.likelyCauses()).isNotEmpty();
        assertThat(response.recommendedChecks()).isNotEmpty();
    }
}
