package com.example.paymentservice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DatabaseFaultSimulationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /support/database/pool returns Hikari pool diagnostics")
    void testSupportDatabasePoolEndpoint() throws Exception {
        mockMvc.perform(get("/support/database/pool"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.poolName").exists())
                .andExpect(jsonPath("$.maxPoolSize").isNumber())
                .andExpect(jsonPath("$.activeConnections").isNumber())
                .andExpect(jsonPath("$.status").value("NORMAL"));
    }

    @Test
    @DisplayName("POST /demo/fault/database/pool-pressure applies and clears connection pressure safely")
    void testPoolPressureSimulation() throws Exception {
        // Apply pressure: 2 connections for 2 seconds
        mockMvc.perform(post("/demo/fault/database/pool-pressure")
                        .param("connections", "2")
                        .param("seconds", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PRESSURE_APPLIED"))
                .andExpect(jsonPath("$.connectionsTarget").value(2));

        // Verify fault status
        mockMvc.perform(get("/demo/fault/database/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentlyHeldConnections").isNumber());

        // Clear faults
        mockMvc.perform(post("/demo/fault/database/clear"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLEARED"));
    }
}
