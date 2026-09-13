package com.example.prodsupport;

import com.example.prodsupport.ai.config.AiProperties;
import com.example.prodsupport.ai.tools.RecentErrorsAiTool;
import com.example.prodsupport.ai.tools.model.RecentErrorsData;
import com.example.prodsupport.ai.tools.model.RecentErrorsRequest;
import com.example.prodsupport.ai.tools.model.ToolExecutionResult;
import com.example.prodsupport.application.service.DiagnosticService;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.infrastructure.repository.RegisteredApplicationRepository;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SecuritySanitizationTest {

    private MockWebServer mockWebServer;

    @Autowired
    private RegisteredApplicationRepository repository;

    @Autowired
    private RecentErrorsAiTool errorsTool;

    @Autowired
    private AiProperties aiProperties;

    private RegisteredApplication registeredApp;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        repository.deleteAll();
        String baseUrl = mockWebServer.url("").toString().replaceAll("/+$", "");

        registeredApp = new RegisteredApplication(
                "payment-service",
                "payments",
                "local",
                "Demo payment service",
                baseUrl,
                baseUrl + "/actuator/health",
                baseUrl + "/support/info",
                true
        );
        repository.save(registeredApp);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("Error diagnostics must sanitize passwords, tokens and secrets from messages")
    void shouldSanitizePasswordsAndSecretsFromErrors() {
        String sensitiveJson = """
                {
                  "applicationName": "payment-service",
                  "errors": [
                    {
                      "timestamp": "2026-09-13T20:00:00Z",
                      "level": "ERROR",
                      "type": "AuthException",
                      "message": "Failed with password: superSecretPassword123 and token=abc123secretKey"
                    }
                  ]
                }
                """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(sensitiveJson));

        DiagnosticService liveDiagnosticService = new DiagnosticService(RestClient.builder(), aiProperties);
        RecentErrorsData data = liveDiagnosticService.getRecentErrors(registeredApp, 10);

        assertThat(data.count()).isEqualTo(1);
        String sanitizedMessage = data.errors().get(0).message();

        assertThat(sanitizedMessage).doesNotContain("superSecretPassword123");
        assertThat(sanitizedMessage).doesNotContain("abc123secretKey");
        assertThat(sanitizedMessage).contains("password=***");
        assertThat(sanitizedMessage).contains("token=***");
    }

    @Test
    @DisplayName("Model cannot target arbitrary external URLs; target is resolved strictly from registry")
    void shouldPreventArbitraryUrlTargeting() {
        // A request trying to target an arbitrary URL or unregistered application is rejected
        RecentErrorsRequest maliciousRequest = new RecentErrorsRequest("http://evil-attacker.com", "local", 10);
        ToolExecutionResult<RecentErrorsData> result = errorsTool.apply(maliciousRequest);

        assertThat(result.success()).isFalse();
        assertThat(result.warning()).contains("is not registered");
    }
}
