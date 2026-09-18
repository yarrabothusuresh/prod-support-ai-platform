package com.example.prodsupport.logging;

import com.example.prodsupport.ai.tools.security.ApplicationAccessValidator;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.logging.client.LogSearchClient;
import com.example.prodsupport.logging.config.LoggingProperties;
import com.example.prodsupport.logging.model.ErrorPatternDto;
import com.example.prodsupport.logging.model.ErrorPatternResult;
import com.example.prodsupport.logging.model.LogSearchCriteria;
import com.example.prodsupport.logging.repository.ApplicationLoggingConfigRepository;
import com.example.prodsupport.logging.service.ErrorPatternService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ErrorPatternServiceTest {

    @Mock
    private ApplicationAccessValidator accessValidator;

    @Mock
    private ApplicationLoggingConfigRepository loggingConfigRepository;

    @Mock
    private LogSearchClient logSearchClient;

    private LoggingProperties properties;
    private ErrorPatternService service;
    private RegisteredApplication testApp;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        properties = new LoggingProperties();
        service = new ErrorPatternService(accessValidator, loggingConfigRepository, logSearchClient, properties);

        testApp = new RegisteredApplication("payment-service", "payments", "local",
                "Payment service", "http://localhost:8081", "http://localhost:8081/actuator/health",
                "http://localhost:8081/support/info", true);
        testApp.setId(1L);

        when(accessValidator.validateAndGet("payment-service", "local")).thenReturn(testApp);
        when(loggingConfigRepository.findByApplication(testApp)).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("Should aggregate single error category correctly")
    void testSingleErrorCategory() {
        ErrorPatternResult mockResult = new ErrorPatternResult(
                "payment-service", "local", 15,
                List.of(new ErrorPatternDto("DatabaseConnectionException", 12L)),
                List.of()
        );
        when(logSearchClient.summarizeErrors(any())).thenReturn(mockResult);

        ErrorPatternResult result = service.summarizeErrors("payment-service", "local", 15, 10);

        assertThat(result.patterns()).hasSize(1);
        assertThat(result.patterns().getFirst().errorType()).isEqualTo("DatabaseConnectionException");
        assertThat(result.patterns().getFirst().count()).isEqualTo(12L);
    }

    @Test
    @DisplayName("Should aggregate multiple error categories correctly")
    void testMultipleErrorCategories() {
        ErrorPatternResult mockResult = new ErrorPatternResult(
                "payment-service", "local", 15,
                List.of(
                        new ErrorPatternDto("DatabaseConnectionException", 20L),
                        new ErrorPatternDto("SQLTimeoutException", 8L),
                        new ErrorPatternDto("HttpTimeoutException", 4L)
                ),
                List.of()
        );
        when(logSearchClient.summarizeErrors(any())).thenReturn(mockResult);

        ErrorPatternResult result = service.summarizeErrors("payment-service", "local", 15, 10);

        assertThat(result.patterns()).hasSize(3);
        assertThat(result.patterns().get(0).count()).isEqualTo(20L);
        assertThat(result.patterns().get(1).count()).isEqualTo(8L);
        assertThat(result.patterns().get(2).count()).isEqualTo(4L);
    }

    @Test
    @DisplayName("Should handle zero errors gracefully")
    void testZeroErrors() {
        ErrorPatternResult mockResult = new ErrorPatternResult(
                "payment-service", "local", 15,
                List.of(),
                List.of()
        );
        when(logSearchClient.summarizeErrors(any())).thenReturn(mockResult);

        ErrorPatternResult result = service.summarizeErrors("payment-service", "local", 15, 10);

        assertThat(result.patterns()).isEmpty();
        assertThat(result.warnings()).isEmpty();
    }
}
