package com.example.prodsupport;

import com.example.prodsupport.database.dto.DatabaseConfigDto;
import com.example.prodsupport.database.dto.DatabaseConfigRequest;
import com.example.prodsupport.database.model.DatabaseType;
import com.example.prodsupport.database.service.ApplicationDatabaseConfigService;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.infrastructure.repository.RegisteredApplicationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ApplicationDatabaseConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegisteredApplicationRepository applicationRepository;

    @Autowired
    private ApplicationDatabaseConfigService configService;

    @Autowired
    private ObjectMapper objectMapper;

    private RegisteredApplication app;

    @BeforeEach
    void setUp() {
        applicationRepository.deleteAll();
        app = new RegisteredApplication(
                "payment-service",
                "payments",
                "local",
                "Demo payment service",
                "http://localhost:8081",
                "http://localhost:8081/actuator/health",
                "http://localhost:8081/support/info",
                true
        );
        app = applicationRepository.save(app);
    }

    @Test
    @DisplayName("Configure and retrieve application database diagnostics without leaking credentials")
    void testConfigureAndRetrieveDatabaseConfig() throws Exception {
        DatabaseConfigRequest request = new DatabaseConfigRequest(
                DatabaseType.POSTGRESQL,
                "payment-db",
                "jdbc:postgresql://localhost:5432/payment_db",
                "payment_app",
                "PAYMENT_DB",
                true
        );

        // 1. PUT /api/applications/{id}/database
        mockMvc.perform(put("/api/applications/" + app.getId() + "/database")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("payment-db"))
                .andExpect(jsonPath("$.databaseType").value("POSTGRESQL"))
                .andExpect(jsonPath("$.username").value("payment_app"))
                .andExpect(jsonPath("$.credentialReference").value("PAYMENT_DB"))
                .andExpect(jsonPath("$.password").doesNotExist()); // NEVER exposed

        // 2. GET /api/applications/{id}/database
        mockMvc.perform(get("/api/applications/" + app.getId() + "/database"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("payment-db"))
                .andExpect(jsonPath("$.jdbcUrl").value("jdbc:postgresql://localhost:5432/payment_db"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("Returns 404 for database config of non-existent application")
    void testNonExistentApp() throws Exception {
        mockMvc.perform(get("/api/applications/999999/database"))
                .andExpect(status().isNotFound());
    }
}
