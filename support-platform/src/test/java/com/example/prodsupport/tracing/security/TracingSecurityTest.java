package com.example.prodsupport.tracing.security;

import com.example.prodsupport.ai.tools.security.ApplicationAccessValidator;
import com.example.prodsupport.common.exception.ApplicationDisabledException;
import com.example.prodsupport.common.exception.ApplicationNotFoundException;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.tracing.client.TraceSearchClient;
import com.example.prodsupport.tracing.entity.ApplicationTracingConfigEntity;
import com.example.prodsupport.tracing.model.TraceDetailResult;
import com.example.prodsupport.tracing.model.TraceSearchResult;
import com.example.prodsupport.tracing.repository.ApplicationTracingConfigRepository;
import com.example.prodsupport.tracing.service.TraceSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Tracing Security and Scope Isolation Tests")
class TracingSecurityTest {

    @Mock
    private ApplicationAccessValidator accessValidator;

    @Mock
    private ApplicationTracingConfigRepository configRepository;

    @Mock
    private TraceSearchClient traceSearchClient;

    private TraceSearchService traceSearchService;
    private RegisteredApplication paymentApp;

    @BeforeEach
    void setUp() {
        traceSearchService = new TraceSearchService(accessValidator, configRepository, traceSearchClient);
        paymentApp = new RegisteredApplication(
                "payment-service", "payments", "local",
                "Payment service", "http://localhost:8081", "http://localhost:8081/actuator/health",
                "http://localhost:8081/support/info", true
        );
    }

    @Test
    @DisplayName("Should reject search when application is not found by access validator")
    void testAppNotFound() {
        when(accessValidator.validateAndGet("unknown-app", "local"))
                .thenThrow(new ApplicationNotFoundException("unknown-app", "local"));

        assertThatThrownBy(() -> traceSearchService.searchTraces("unknown-app", "local", 15, 20, false))
                .isInstanceOf(ApplicationNotFoundException.class);
    }

    @Test
    @DisplayName("Should reject search when application is disabled by access validator")
    void testAppDisabled() {
        when(accessValidator.validateAndGet("disabled-app", "local"))
                .thenThrow(new ApplicationDisabledException("disabled-app", "local"));

        assertThatThrownBy(() -> traceSearchService.searchTraces("disabled-app", "local", 15, 20, false))
                .isInstanceOf(ApplicationDisabledException.class);
    }

    @Test
    @DisplayName("Should return warning and empty traces when tracing is disabled in config")
    void testTracingDisabledInConfig() {
        when(accessValidator.validateAndGet("payment-service", "local")).thenReturn(paymentApp);

        ApplicationTracingConfigEntity disabledConfig = new ApplicationTracingConfigEntity(
                paymentApp, false, "payment-service", "local", "http://localhost:16686"
        );
        when(configRepository.findByApplicationNameAndEnvironment("payment-service", "local"))
                .thenReturn(Optional.of(disabledConfig));

        TraceSearchResult result = traceSearchService.searchTraces("payment-service", "local", 15, 20, false);

        assertThat(result.traces()).isEmpty();
        assertThat(result.warnings()).anyMatch(w -> w.contains("Distributed tracing is disabled"));
    }

    @Test
    @DisplayName("Should isolate queries to the configured application telemetry service name")
    void testServiceScopeIsolation() {
        when(accessValidator.validateAndGet("payment-service", "local")).thenReturn(paymentApp);

        ApplicationTracingConfigEntity config = new ApplicationTracingConfigEntity(
                paymentApp, true, "payment-service-telemetry", "local", "http://localhost:16686"
        );
        when(configRepository.findByApplicationNameAndEnvironment("payment-service", "local"))
                .thenReturn(Optional.of(config));

        when(traceSearchClient.searchTraces(eq("payment-service-telemetry"), eq("local"), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(new TraceSearchResult("payment-service", "local", Collections.emptyList(), Collections.emptyList()));

        traceSearchService.searchTraces("payment-service", "local", 30, 10, true);

        verify(traceSearchClient).searchTraces("payment-service-telemetry", "local", 30, 10, true);
    }

    @Test
    @DisplayName("Should reject malformed trace IDs with regex guard before querying backend")
    void testMalformedTraceIdValidation() {
        assertThatThrownBy(() -> traceSearchService.getTraceDetails("payment-service", "local", "invalid;drop-table"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid traceId format");
    }
}
