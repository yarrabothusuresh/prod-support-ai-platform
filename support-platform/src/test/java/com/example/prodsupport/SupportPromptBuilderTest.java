package com.example.prodsupport;

import com.example.prodsupport.ai.model.ApplicationSupportContext;
import com.example.prodsupport.ai.model.SupportDependencyDto;
import com.example.prodsupport.ai.model.SupportErrorDto;
import com.example.prodsupport.ai.prompt.SupportPromptBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SupportPromptBuilderTest {

    private SupportPromptBuilder promptBuilder;

    @BeforeEach
    void setUp() {
        promptBuilder = new SupportPromptBuilder();
    }

    @Test
    @DisplayName("System prompt should establish anti-hallucination and safe production support rules")
    void systemPromptShouldContainCoreRules() {
        String systemPrompt = promptBuilder.buildSystemPrompt();

        assertThat(systemPrompt)
                .contains("FACT != INFERENCE")
                .contains("ZERO HALLUCINATION")
                .contains("UNCONFIRMED ROOT CAUSES")
                .contains("NO DESTRUCTIVE ACTIONS")
                .contains("summary")
                .contains("observedFacts")
                .contains("possibleCauses")
                .contains("recommendedChecks")
                .contains("confidence");
    }

    @Test
    @DisplayName("User prompt should include application metadata, live telemetry, warnings, and question")
    void userPromptShouldIncludeAllContextElements() {
        ApplicationSupportContext context = new ApplicationSupportContext(
                "payment-service",
                "payments-team",
                "local",
                "Core payment processing service",
                "http://localhost:8081",
                "UP",
                "UP",
                true,
                true,
                OffsetDateTime.now(),
                List.of("Downstream fraud service responded slowly")
        );

        String userQuestion = "Is the payment service healthy and what should I check if users report failures?";
        String userPrompt = promptBuilder.buildUserPrompt(context, userQuestion);

        assertThat(userPrompt)
                .contains("payment-service")
                .contains("local")
                .contains("payments-team")
                .contains("Core payment processing service")
                .contains("http://localhost:8081")
                .contains("Support Info Status: UP (Endpoint Reachable)")
                .contains("Actuator Health Status: UP (Endpoint Reachable)")
                .contains("Downstream fraud service responded slowly")
                .contains(userQuestion);
    }

    @Test
    @DisplayName("User prompt should handle empty warnings cleanly")
    void userPromptShouldHandleEmptyWarnings() {
        ApplicationSupportContext context = new ApplicationSupportContext(
                "auth-service",
                "security",
                "staging",
                null,
                "http://localhost:8082",
                "UP",
                "UNKNOWN",
                true,
                false,
                OffsetDateTime.now(),
                List.of()
        );

        String userPrompt = promptBuilder.buildUserPrompt(context, "Why is auth failing?");

        assertThat(userPrompt)
                .contains("None (All telemetry endpoints responded normally)")
                .contains("Why is auth failing?");
    }

    @Test
    @DisplayName("User prompt should format recent errors and downstream dependencies")
    void userPromptShouldFormatErrorsAndDependencies() {
        ApplicationSupportContext context = new ApplicationSupportContext(
                "payment-service",
                "payments",
                "prod",
                "Payment API",
                "http://payment-prod:8081",
                "UP",
                "UP",
                true,
                true,
                OffsetDateTime.now(),
                List.of(),
                List.of(
                        new SupportErrorDto(Instant.parse("2026-09-10T12:00:00Z"), "ERROR", "DatabaseTimeoutException", "Connection to primary replica timed out")
                ),
                List.of(
                        new SupportDependencyDto("postgres-db", "DATABASE", "DOWN"),
                        new SupportDependencyDto("notification-service", "HTTP", "UP")
                ),
                true,
                true
        );

        String prompt = promptBuilder.buildUserPrompt(context, "Why are payments failing?");

        assertThat(prompt)
                .contains("=== RECENT APPLICATION ERRORS ===")
                .contains("DatabaseTimeoutException: Connection to primary replica timed out")
                .contains("=== DOWNSTREAM DEPENDENCY HEALTH ===")
                .contains("- postgres-db (Type: DATABASE): status=DOWN")
                .contains("- notification-service (Type: HTTP): status=UP");
    }
}
