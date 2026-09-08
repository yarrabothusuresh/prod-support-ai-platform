package com.example.prodsupport;

import com.example.prodsupport.application.dto.TestConnectionResponse;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.infrastructure.client.SupportClient;
import com.example.prodsupport.infrastructure.repository.RegisteredApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TestConnectionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegisteredApplicationRepository repository;

    @MockBean
    private SupportClient supportClient;

    private RegisteredApplication savedApp;

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
        savedApp = repository.save(app);
    }

    @Test
    void shouldReturnSuccessWhenConnectionSucceeds() throws Exception {
        when(supportClient.testConnection(eq(savedApp.getSupportInfoUrl())))
                .thenReturn(TestConnectionResponse.success("payment-service", "UP", 25L));

        mockMvc.perform(post("/api/applications/" + savedApp.getId() + "/test-connection"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.applicationName").value("payment-service"))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.responseTimeMs").value(25))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void shouldReturnCleanFailureWhenConnectionFails() throws Exception {
        when(supportClient.testConnection(eq(savedApp.getSupportInfoUrl())))
                .thenReturn(TestConnectionResponse.failure("Connection refused"));

        mockMvc.perform(post("/api/applications/" + savedApp.getId() + "/test-connection"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(false))
                .andExpect(jsonPath("$.error").value("Connection refused"))
                .andExpect(jsonPath("$.applicationName").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.responseTimeMs").doesNotExist());
    }

    @Test
    void shouldReturnNotFoundWhenApplicationDoesNotExist() throws Exception {
        mockMvc.perform(post("/api/applications/99999/test-connection"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message", containsString("99999")));
    }
}
