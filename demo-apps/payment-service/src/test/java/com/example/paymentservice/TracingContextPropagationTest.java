package com.example.paymentservice;

import com.example.paymentservice.dto.CreatePaymentRequest;
import com.example.paymentservice.filter.CorrelationIdFilter;
import com.example.paymentservice.service.PaymentProducer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Tracing Context and Fault Simulation Tests")
class TracingContextPropagationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentProducer paymentProducer;

    @Test
    @DisplayName("POST /api/payments should process payment and return active traceId and spanId")
    void testPaymentTracedRequest() throws Exception {
        CreatePaymentRequest request = new CreatePaymentRequest(
                "PAY-TEST-TRACE-1", new BigDecimal("149.99"), "USD"
        );

        mockMvc.perform(post("/api/payments")
                        .header(CorrelationIdFilter.CORRELATION_ID_HEADER, "CORR-TEST-TRACE-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(header().string(CorrelationIdFilter.CORRELATION_ID_HEADER, "CORR-TEST-TRACE-1"))
                .andExpect(jsonPath("$.paymentId").value("PAY-TEST-TRACE-1"))
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.traceId").value(notNullValue()))
                .andExpect(jsonPath("$.spanId").value(notNullValue()));
    }

    @Test
    @DisplayName("POST /demo/fault/tracing/slow-payment should simulate delay and return traceId")
    void testSlowPaymentSimulation() throws Exception {
        mockMvc.perform(post("/demo/fault/tracing/slow-payment")
                        .param("delayMs", "200"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.simulatedDelayMs").value(200))
                .andExpect(jsonPath("$.traceId").value(notNullValue()))
                .andExpect(jsonPath("$.spanId").value(notNullValue()));
    }

    @Test
    @DisplayName("POST /demo/fault/tracing/payment-error should simulate failure, mark span error, and return traceId")
    void testPaymentErrorSimulation() throws Exception {
        mockMvc.perform(post("/demo/fault/tracing/payment-error")
                        .param("errorType", "PaymentGatewayException")
                        .param("message", "Simulated payment processor outage"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorType").value("PaymentGatewayException"))
                .andExpect(jsonPath("$.message").value("Simulated payment processor outage"))
                .andExpect(jsonPath("$.traceId").value(notNullValue()));
    }
}
