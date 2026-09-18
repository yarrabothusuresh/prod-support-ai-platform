package com.example.prodsupport.logging;

import com.example.prodsupport.ai.tools.security.ApplicationAccessValidator;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.logging.client.LogSearchClient;
import com.example.prodsupport.logging.config.LoggingProperties;
import com.example.prodsupport.logging.model.LogSearchCriteria;
import com.example.prodsupport.logging.model.LogTimelineEventDto;
import com.example.prodsupport.logging.model.LogTimelineResult;
import com.example.prodsupport.logging.repository.ApplicationLoggingConfigRepository;
import com.example.prodsupport.logging.service.LogTimelineService;
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

class LogTimelineServiceTest {

    @Mock
    private ApplicationAccessValidator accessValidator;

    @Mock
    private ApplicationLoggingConfigRepository loggingConfigRepository;

    @Mock
    private LogSearchClient logSearchClient;

    private LoggingProperties properties;
    private LogTimelineService service;
    private RegisteredApplication testApp;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        properties = new LoggingProperties();
        service = new LogTimelineService(accessValidator, loggingConfigRepository, logSearchClient, properties);

        testApp = new RegisteredApplication("payment-service", "payments", "local",
                "Payment service", "http://localhost:8081", "http://localhost:8081/actuator/health",
                "http://localhost:8081/support/info", true);
        testApp.setId(1L);

        when(accessValidator.validateAndGet("payment-service", "local")).thenReturn(testApp);
        when(loggingConfigRepository.findByApplication(testApp)).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("Should return chronological log events for given correlation ID")
    void testTimelineWithCorrelationId() {
        LogTimelineEventDto e1 = new LogTimelineEventDto("2026-09-17T10:00:01Z", "INFO", "http", null, "Payment received", "CORR-999");
        LogTimelineEventDto e2 = new LogTimelineEventDto("2026-09-17T10:00:03Z", "WARN", "database", null, "Slow response", "CORR-999");
        LogTimelineEventDto e3 = new LogTimelineEventDto("2026-09-17T10:00:07Z", "ERROR", "database", "DatabaseConnectionException", "Timeout", "CORR-999");

        LogTimelineResult mockResult = new LogTimelineResult("payment-service", "local", List.of(e1, e2, e3), List.of());
        when(logSearchClient.getTimeline(any())).thenReturn(mockResult);

        LogTimelineResult result = service.getTimeline("payment-service", "local",
                Instant.now().minusSeconds(600), Instant.now(), "CORR-999", 50);

        assertThat(result.events()).hasSize(3);
        assertThat(result.events().get(0).level()).isEqualTo("INFO");
        assertThat(result.events().get(2).level()).isEqualTo("ERROR");

        ArgumentCaptor<LogSearchCriteria> captor = ArgumentCaptor.forClass(LogSearchCriteria.class);
        verify(logSearchClient).getTimeline(captor.capture());
        assertThat(captor.getValue().correlationId()).isEqualTo("CORR-999");
        assertThat(captor.getValue().applicationName()).isEqualTo("payment-service");
        assertThat(captor.getValue().environment()).isEqualTo("local");
    }
}
