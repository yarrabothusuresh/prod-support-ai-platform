package com.example.prodsupport.logging;

import com.example.prodsupport.ai.tools.security.ApplicationAccessValidator;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.logging.client.LogSearchClient;
import com.example.prodsupport.logging.config.LoggingProperties;
import com.example.prodsupport.logging.entity.ApplicationLoggingConfigEntity;
import com.example.prodsupport.logging.model.LogEntryDto;
import com.example.prodsupport.logging.model.LogSearchCriteria;
import com.example.prodsupport.logging.model.LogSearchResult;
import com.example.prodsupport.logging.repository.ApplicationLoggingConfigRepository;
import com.example.prodsupport.logging.service.LogSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LogSearchServiceTest {

    @Mock
    private ApplicationAccessValidator accessValidator;

    @Mock
    private ApplicationLoggingConfigRepository loggingConfigRepository;

    @Mock
    private LogSearchClient logSearchClient;

    private LoggingProperties properties;
    private LogSearchService service;

    private RegisteredApplication testApp;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        properties = new LoggingProperties();
        service = new LogSearchService(accessValidator, loggingConfigRepository, logSearchClient, properties);

        testApp = new RegisteredApplication("payment-service", "payments", "local",
                "Payment service", "http://localhost:8081", "http://localhost:8081/actuator/health",
                "http://localhost:8081/support/info", true);
        testApp.setId(1L);

        when(accessValidator.validateAndGet("payment-service", "local")).thenReturn(testApp);
    }

    @Test
    @DisplayName("Should execute scoped log search when application is valid and logging is enabled")
    void testSuccessfulSearch() {
        LogEntryDto logEntry = new LogEntryDto(
                "2026-09-17T10:00:00Z", "ERROR", "DatabaseConnectionException",
                "Connection timed out", "CORR-001", "database", "PaymentDao", "TRACE-1"
        );
        LogSearchResult mockResult = new LogSearchResult(
                "payment-service", "local", 1, false, List.of(logEntry), List.of()
        );

        when(loggingConfigRepository.findByApplication(testApp)).thenReturn(Optional.of(
                new ApplicationLoggingConfigEntity(testApp, true, "ELASTICSEARCH", "prod-support-logs-*")
        ));
        when(logSearchClient.search(any())).thenReturn(mockResult);

        LogSearchResult result = service.searchLogs("payment-service", "local",
                Instant.now().minusSeconds(600), Instant.now(), List.of("ERROR"), "timeout", null, 20);

        assertThat(result.totalHits()).isEqualTo(1);
        assertThat(result.logs()).hasSize(1);
        assertThat(result.logs().getFirst().errorType()).isEqualTo("DatabaseConnectionException");

        ArgumentCaptor<LogSearchCriteria> captor = ArgumentCaptor.forClass(LogSearchCriteria.class);
        verify(logSearchClient).search(captor.capture());
        assertThat(captor.getValue().applicationName()).isEqualTo("payment-service");
        assertThat(captor.getValue().environment()).isEqualTo("local");
        assertThat(captor.getValue().indexPattern()).isEqualTo("prod-support-logs-*");
    }

    @Test
    @DisplayName("Should return empty result and warning when logging is disabled for application")
    void testLoggingDisabledForApp() {
        when(loggingConfigRepository.findByApplication(testApp)).thenReturn(Optional.of(
                new ApplicationLoggingConfigEntity(testApp, false, "ELASTICSEARCH", "prod-support-logs-*")
        ));

        LogSearchResult result = service.searchLogs("payment-service", "local",
                null, null, List.of("ERROR"), null, null, 20);

        assertThat(result.totalHits()).isEqualTo(0);
        assertThat(result.logs()).isEmpty();
        assertThat(result.warnings()).anyMatch(w -> w.contains("disabled for application"));
        verifyNoInteractions(logSearchClient);
    }

    @Test
    @DisplayName("Should handle Elasticsearch client unavailability and return warnings")
    void testElasticsearchUnavailable() {
        when(loggingConfigRepository.findByApplication(testApp)).thenReturn(Optional.empty());
        when(logSearchClient.search(any())).thenReturn(new LogSearchResult(
                "payment-service", "local", 0, false, List.of(),
                List.of("Centralized log search currently unavailable (Connection refused)")
        ));

        LogSearchResult result = service.searchLogs("payment-service", "local",
                null, null, List.of("ERROR"), null, null, 20);

        assertThat(result.totalHits()).isEqualTo(0);
        assertThat(result.logs()).isEmpty();
        assertThat(result.warnings()).anyMatch(w -> w.contains("unavailable"));
    }
}
