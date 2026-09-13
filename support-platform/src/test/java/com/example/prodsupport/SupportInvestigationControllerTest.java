package com.example.prodsupport;

import com.example.prodsupport.application.dto.SupportInvestigationResponse;
import com.example.prodsupport.application.service.InvestigationService;
import com.example.prodsupport.common.exception.ApplicationDisabledException;
import com.example.prodsupport.common.exception.ApplicationNotFoundException;
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
class SupportInvestigationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegisteredApplicationRepository repository;

    @MockBean
    private InvestigationService investigationService;

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
    @DisplayName("POST /api/support/investigate should return 200 OK with full investigation response")
    void shouldReturnInvestigationResponse() throws Exception {
        SupportInvestigationResponse mockResponse = new SupportInvestigationResponse(
                "payment-service",
                "local",
                "The application is available but its database dependency is unhealthy.",
                List.of("Application health is UP", "PostgreSQL dependency is DOWN"),
                List.of("Loss of database connectivity"),
                List.of("Verify PostgreSQL availability"),
                "HIGH",
                List.of("check_application_health", "check_dependencies"),
                2,
                List.of()
        );

        when(investigationService.investigate(any())).thenReturn(mockResponse);

        String requestBody = """
                {
                  "applicationName": "payment-service",
                  "environment": "local",
                  "question": "Why are payments failing?"
                }
                """;

        mockMvc.perform(post("/api/support/investigate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationName", is("payment-service")))
                .andExpect(jsonPath("$.environment", is("local")))
                .andExpect(jsonPath("$.summary", containsString("database dependency is unhealthy")))
                .andExpect(jsonPath("$.confidence", is("HIGH")))
                .andExpect(jsonPath("$.toolsUsed", hasItems("check_application_health", "check_dependencies")))
                .andExpect(jsonPath("$.toolExecutionCount", is(2)))
                .andExpect(jsonPath("$.observedFacts", hasSize(2)))
                .andExpect(jsonPath("$.likelyCauses", hasSize(1)))
                .andExpect(jsonPath("$.recommendedChecks", hasSize(1)));
    }

    @Test
    @DisplayName("POST /api/support/investigate with missing required fields should return 400 Bad Request")
    void shouldReturn400WhenRequiredFieldsMissing() throws Exception {
        String requestBody = """
                {
                  "environment": "local"
                }
                """;

        mockMvc.perform(post("/api/support/investigate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("applicationName is required")));
    }

    @Test
    @DisplayName("POST /api/support/investigate for non-existent application should return 404 Not Found")
    void shouldReturn404WhenApplicationNotFound() throws Exception {
        when(investigationService.investigate(any()))
                .thenThrow(new ApplicationNotFoundException("ghost-service", "local"));

        String requestBody = """
                {
                  "applicationName": "ghost-service",
                  "environment": "local",
                  "question": "Is it running?"
                }
                """;

        mockMvc.perform(post("/api/support/investigate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Application ghost-service in environment local is not registered")));
    }

    @Test
    @DisplayName("POST /api/support/investigate for disabled application should return 400 Bad Request")
    void shouldReturn400WhenApplicationDisabled() throws Exception {
        when(investigationService.investigate(any()))
                .thenThrow(new ApplicationDisabledException("legacy-service", "local"));

        String requestBody = """
                {
                  "applicationName": "legacy-service",
                  "environment": "local",
                  "question": "Check status"
                }
                """;

        mockMvc.perform(post("/api/support/investigate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("is disabled and cannot be diagnosed")));
    }
}
