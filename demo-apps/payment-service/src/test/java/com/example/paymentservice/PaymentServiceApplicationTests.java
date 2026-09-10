package com.example.paymentservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentServiceApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnPaymentStatus() throws Exception {
        mockMvc.perform(get("/api/payments/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("payment-service"))
                .andExpect(jsonPath("$.message").value("Payment service is running"));
    }

    @Test
    void shouldExposeSupportInfoFromStarter() throws Exception {
        mockMvc.perform(get("/support/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationName").value("payment-service"))
                .andExpect(jsonPath("$.team").value("payments"))
                .andExpect(jsonPath("$.environment").value("local"))
                .andExpect(jsonPath("$.description").value("Demo payment processing service"))
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void shouldExposeActuatorHealth() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void shouldExposeSupportErrorsAndSimulateError() throws Exception {
        mockMvc.perform(get("/support/errors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationName").value("payment-service"));

        // Simulate an error
        mockMvc.perform(post("/api/payments/simulate/error")
                        .param("type", "PaymentGatewayTimeout")
                        .param("message", "Stripe API timed out after 5000ms")
                        .param("level", "ERROR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECORDED"));

        // Verify it appears in /support/errors
        mockMvc.perform(get("/support/errors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors[0].type").value("PaymentGatewayTimeout"))
                .andExpect(jsonPath("$.errors[0].message").value("Stripe API timed out after 5000ms"));
    }

    @Test
    void shouldExposeSupportDependenciesAndSimulateOverride() throws Exception {
        // Initial dependencies check
        mockMvc.perform(get("/support/dependencies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationName").value("payment-service"))
                .andExpect(jsonPath("$.dependencies", hasSize(3)))
                .andExpect(jsonPath("$.dependencies[0].name").value("postgres-db"))
                .andExpect(jsonPath("$.dependencies[0].status").value("UP"));

        // Simulate dependency outage
        mockMvc.perform(post("/api/payments/simulate/dependency")
                        .param("dependency", "postgres-db")
                        .param("status", "DOWN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overriddenStatus").value("DOWN"));

        // Verify status is now DOWN
        mockMvc.perform(get("/support/dependencies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dependencies[0].status").value("DOWN"));

        // Clear overrides
        mockMvc.perform(delete("/api/payments/simulate/dependency"))
                .andExpect(status().isOk());

        // Verify restored to UP
        mockMvc.perform(get("/support/dependencies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dependencies[0].status").value("UP"));
    }
}
