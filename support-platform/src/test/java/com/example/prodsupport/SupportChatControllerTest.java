package com.example.prodsupport;

import com.example.prodsupport.ai.client.SupportAiClient;
import com.example.prodsupport.ai.model.SupportAiResult;
import com.example.prodsupport.common.exception.AiServiceUnavailableException;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.infrastructure.repository.RegisteredApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SupportChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegisteredApplicationRepository repository;

    @MockBean
    private SupportAiClient supportAiClient;

    @MockBean
    private com.example.prodsupport.ai.service.ApplicationContextService contextService;

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
    @DisplayName("POST /api/support/chat should return 200 OK with structured support response")
    void shouldReturnStructuredResponseForValidRequest() throws Exception {
        com.example.prodsupport.ai.model.ApplicationSupportContext mockContext =
                new com.example.prodsupport.ai.model.ApplicationSupportContext(
                        "payment-service",
                        "payments",
                        "local",
                        "Demo payment service",
                        "http://localhost:8081",
                        "UP",
                        "UP",
                        true,
                        true,
                        java.time.OffsetDateTime.now(),
                        List.of()
                );

        when(contextService.collectContext(any(RegisteredApplication.class))).thenReturn(mockContext);

        when(supportAiClient.analyze(any(), any()))
                .thenReturn(new SupportAiResult(
                        "The payment service is healthy and operational.",
                        List.of("Support endpoint reports UP", "Actuator health reports UP"),
                        List.of("No failures detected"),
                        List.of("Check payment gateway response times", "Monitor queue depth"),
                        "HIGH",
                        List.of()
                ));

        String requestBody = """
                {
                  "applicationName": "payment-service",
                  "environment": "local",
                  "question": "Is the payment service healthy and what should I check?"
                }
                """;

        mockMvc.perform(post("/api/support/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationName", is("payment-service")))
                .andExpect(jsonPath("$.environment", is("local")))
                .andExpect(jsonPath("$.summary", containsString("healthy")))
                .andExpect(jsonPath("$.observedFacts", hasSize(2)))
                .andExpect(jsonPath("$.possibleCauses", hasSize(1)))
                .andExpect(jsonPath("$.recommendedChecks", hasSize(2)))
                .andExpect(jsonPath("$.confidence", is("HIGH")))
                .andExpect(jsonPath("$.warnings", empty()));
    }

    @Test
    @DisplayName("POST /api/support/chat should return 400 Bad Request when mandatory fields are missing")
    void shouldReturnBadRequestWhenFieldsAreMissing() throws Exception {
        String invalidRequest = """
                {
                  "applicationName": "",
                  "environment": "local",
                  "question": ""
                }
                """;

        mockMvc.perform(post("/api/support/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")));
    }

    @Test
    @DisplayName("POST /api/support/chat should return 404 Not Found when application is not registered")
    void shouldReturnNotFoundWhenAppIsNotRegistered() throws Exception {
        String requestBody = """
                {
                  "applicationName": "unknown-service",
                  "environment": "local",
                  "question": "Is it up?"
                }
                """;

        mockMvc.perform(post("/api/support/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", is("Application unknown-service in environment local is not registered")));
    }

    @Test
    @DisplayName("POST /api/support/chat should return 503 Service Unavailable when local AI is offline")
    void shouldReturnServiceUnavailableWhenAiIsOffline() throws Exception {
        when(supportAiClient.analyze(any(), any()))
                .thenThrow(new AiServiceUnavailableException("Local AI model is currently unavailable"));

        String requestBody = """
                {
                  "applicationName": "payment-service",
                  "environment": "local",
                  "question": "Is the service healthy?"
                }
                """;

        mockMvc.perform(post("/api/support/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status", is(503)))
                .andExpect(jsonPath("$.error", is("Service Unavailable")))
                .andExpect(jsonPath("$.message", is("Local AI model is currently unavailable")))
                .andExpect(jsonPath("$.path", is("/api/support/chat")));
    }
}
