package com.example.prodsupport.logging;

import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.infrastructure.repository.RegisteredApplicationRepository;
import com.example.prodsupport.logging.client.LogSearchClient;
import com.example.prodsupport.logging.config.LoggingProperties;
import com.example.prodsupport.logging.controller.LogSearchController;
import com.example.prodsupport.logging.controller.LoggingStatusController;
import com.example.prodsupport.logging.model.*;
import com.example.prodsupport.logging.service.ErrorPatternService;
import com.example.prodsupport.logging.service.LogSearchService;
import com.example.prodsupport.logging.service.LogTimelineService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LogSearchControllerTest {

    private MockMvc mockMvc;
    private RegisteredApplicationRepository applicationRepository;
    private LogSearchService logSearchService;
    private ErrorPatternService errorPatternService;
    private LogTimelineService logTimelineService;
    private LogSearchClient logSearchClient;
    private LoggingProperties properties;
    private ObjectMapper objectMapper;

    private RegisteredApplication testApp;

    @BeforeEach
    void setUp() {
        applicationRepository = mock(RegisteredApplicationRepository.class);
        logSearchService = mock(LogSearchService.class);
        errorPatternService = mock(ErrorPatternService.class);
        logTimelineService = mock(LogTimelineService.class);
        logSearchClient = mock(LogSearchClient.class);
        properties = new LoggingProperties();
        objectMapper = new ObjectMapper();

        LogSearchController searchController = new LogSearchController(
                applicationRepository, logSearchService, errorPatternService, logTimelineService
        );
        LoggingStatusController statusController = new LoggingStatusController(logSearchClient, properties);

        mockMvc = MockMvcBuilders.standaloneSetup(searchController, statusController).build();

        testApp = new RegisteredApplication("payment-service", "payments", "local",
                "Payment service", "http://localhost:8081", "http://localhost:8081/actuator/health",
                "http://localhost:8081/support/info", true);
        testApp.setId(1L);

        when(applicationRepository.findById(1L)).thenReturn(Optional.of(testApp));
    }

    @Test
    @DisplayName("POST /api/applications/{id}/logs/search should return 200 OK with log search results")
    void testSearchLogsEndpoint() throws Exception {
        LogEntryDto entry = new LogEntryDto("2026-09-17T10:00:00Z", "ERROR", "DatabaseConnectionException",
                "Unable to acquire database connection", "CORR-1", "database", "Dao", null);
        LogSearchResult searchResult = new LogSearchResult("payment-service", "local", 1, false, List.of(entry), List.of());

        when(logSearchService.searchLogs(eq("payment-service"), eq("local"), any(), any(), any(), any(), any(), any()))
                .thenReturn(searchResult);

        mockMvc.perform(post("/api/applications/1/logs/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"levels\":[\"ERROR\"],\"limit\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationName").value("payment-service"))
                .andExpect(jsonPath("$.totalHits").value(1))
                .andExpect(jsonPath("$.logs[0].errorType").value("DatabaseConnectionException"));
    }

    @Test
    @DisplayName("GET /api/applications/{id}/logs/error-patterns should return 200 OK with aggregated error categories")
    void testErrorPatternsEndpoint() throws Exception {
        ErrorPatternResult patternResult = new ErrorPatternResult("payment-service", "local", 15,
                List.of(new ErrorPatternDto("DatabaseConnectionException", 8L)), List.of());

        when(errorPatternService.summarizeErrors(eq("payment-service"), eq("local"), eq(15), eq(10)))
                .thenReturn(patternResult);

        mockMvc.perform(get("/api/applications/1/logs/error-patterns?minutes=15&limit=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationName").value("payment-service"))
                .andExpect(jsonPath("$.patterns[0].errorType").value("DatabaseConnectionException"))
                .andExpect(jsonPath("$.patterns[0].count").value(8));
    }

    @Test
    @DisplayName("POST /api/applications/{id}/logs/timeline should return 200 OK with chronological events")
    void testTimelineEndpoint() throws Exception {
        LogTimelineResult timelineResult = new LogTimelineResult("payment-service", "local",
                List.of(new LogTimelineEventDto("2026-09-17T10:00:00Z", "ERROR", "db", "Timeout", "Timeout", "CORR-1")),
                List.of());

        when(logTimelineService.getTimeline(eq("payment-service"), eq("local"), any(), any(), eq("CORR-1"), any()))
                .thenReturn(timelineResult);

        mockMvc.perform(post("/api/applications/1/logs/timeline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correlationId\":\"CORR-1\",\"limit\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationName").value("payment-service"))
                .andExpect(jsonPath("$.events[0].correlationId").value("CORR-1"));
    }

    @Test
    @DisplayName("GET /api/logging/status should return 200 OK with status and provider")
    void testLoggingStatusEndpoint() throws Exception {
        when(logSearchClient.isAvailable()).thenReturn(true);

        mockMvc.perform(get("/api/logging/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("elasticsearch"))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.indexPattern").value("prod-support-logs-*"));
    }
}
