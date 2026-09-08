package com.example.prodsupport;

import com.example.prodsupport.application.dto.RegisterApplicationRequest;
import com.example.prodsupport.infrastructure.repository.RegisteredApplicationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ApplicationRegistryTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RegisteredApplicationRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void shouldRegisterApplicationSuccessfully() throws Exception {
        RegisterApplicationRequest request = new RegisterApplicationRequest(
                "payment-service",
                "payments",
                "local",
                "Demo payment processing service",
                "http://localhost:8081"
        );

        mockMvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.applicationName").value("payment-service"))
                .andExpect(jsonPath("$.team").value("payments"))
                .andExpect(jsonPath("$.environment").value("local"))
                .andExpect(jsonPath("$.description").value("Demo payment processing service"))
                .andExpect(jsonPath("$.baseUrl").value("http://localhost:8081"))
                .andExpect(jsonPath("$.healthUrl").value("http://localhost:8081/actuator/health"))
                .andExpect(jsonPath("$.supportInfoUrl").value("http://localhost:8081/support/info"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void shouldRejectDuplicateApplicationRegistration() throws Exception {
        RegisterApplicationRequest request = new RegisterApplicationRequest(
                "payment-service",
                "payments",
                "local",
                "Demo payment processing service",
                "http://localhost:8081"
        );

        // First registration succeeds
        mockMvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Second registration fails with 409 Conflict
        mockMvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message", containsString("already registered")));
    }

    @Test
    void shouldRejectInvalidRegistrationRequest() throws Exception {
        RegisterApplicationRequest invalidRequest = new RegisterApplicationRequest(
                "", // missing applicationName
                "payments",
                "", // missing environment
                "Description",
                ""  // missing baseUrl
        );

        mockMvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message", containsString("required")));
    }

    @Test
    void shouldRetrieveAndListRegisteredApplications() throws Exception {
        RegisterApplicationRequest request1 = new RegisterApplicationRequest(
                "payment-service", "payments", "local", "Payment service", "http://localhost:8081"
        );
        RegisterApplicationRequest request2 = new RegisterApplicationRequest(
                "order-service", "orders", "local", "Order service", "http://localhost:8082"
        );

        mockMvc.perform(post("/api/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1))).andExpect(status().isCreated());

        mockMvc.perform(post("/api/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2))).andExpect(status().isCreated());

        mockMvc.perform(get("/api/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].applicationName", containsInAnyOrder("payment-service", "order-service")));
    }

    @Test
    void shouldDeleteApplication() throws Exception {
        RegisterApplicationRequest request = new RegisterApplicationRequest(
                "payment-service", "payments", "local", "Payment service", "http://localhost:8081"
        );

        String responseJson = mockMvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Number id = com.jayway.jsonpath.JsonPath.read(responseJson, "$.id");

        mockMvc.perform(delete("/api/applications/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/applications/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
