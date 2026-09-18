package com.example.prodsupport.logging;

import com.example.prodsupport.ai.tools.GetApplicationLogTimelineAiTool;
import com.example.prodsupport.ai.tools.GetErrorPatternSummaryAiTool;
import com.example.prodsupport.ai.tools.SearchApplicationErrorsAiTool;
import com.example.prodsupport.ai.tools.audit.ToolExecutionAuditor;
import com.example.prodsupport.ai.tools.model.*;
import com.example.prodsupport.ai.tools.security.ApplicationAccessValidator;
import com.example.prodsupport.ai.tools.security.ToolAllowlist;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.logging.model.*;
import com.example.prodsupport.logging.service.ErrorPatternService;
import com.example.prodsupport.logging.service.LogSearchService;
import com.example.prodsupport.logging.service.LogTimelineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiLogToolsTest {

    @Mock
    private ApplicationAccessValidator accessValidator;

    @Mock
    private LogSearchService logSearchService;

    @Mock
    private ErrorPatternService errorPatternService;

    @Mock
    private LogTimelineService logTimelineService;

    @Mock
    private ToolExecutionAuditor auditor;

    private SearchApplicationErrorsAiTool searchErrorsTool;
    private GetErrorPatternSummaryAiTool errorPatternTool;
    private GetApplicationLogTimelineAiTool timelineTool;

    private RegisteredApplication testApp;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        searchErrorsTool = new SearchApplicationErrorsAiTool(accessValidator, logSearchService, auditor);
        errorPatternTool = new GetErrorPatternSummaryAiTool(accessValidator, errorPatternService, auditor);
        timelineTool = new GetApplicationLogTimelineAiTool(accessValidator, logTimelineService, auditor);

        testApp = new RegisteredApplication("payment-service", "payments", "local",
                "Payment service", "http://localhost:8081", "http://localhost:8081/actuator/health",
                "http://localhost:8081/support/info", true);
        testApp.setId(1L);

        when(accessValidator.validateAndGet("payment-service", "local")).thenReturn(testApp);
    }

    @Test
    @DisplayName("Verify all 3 logging tools are in approved ToolAllowlist")
    void testToolAllowlist() {
        assertThat(ToolAllowlist.isAllowed(SearchApplicationErrorsAiTool.TOOL_NAME)).isTrue();
        assertThat(ToolAllowlist.isAllowed(GetErrorPatternSummaryAiTool.TOOL_NAME)).isTrue();
        assertThat(ToolAllowlist.isAllowed(GetApplicationLogTimelineAiTool.TOOL_NAME)).isTrue();
        assertThat(ToolAllowlist.isAllowed("execute_elasticsearch_query")).isFalse();
        assertThat(ToolAllowlist.isAllowed("run_elasticsearch_dsl")).isFalse();
    }

    @Test
    @DisplayName("search_application_errors executes read-only query and records audit")
    void testSearchApplicationErrors() {
        LogEntryDto entry = new LogEntryDto("2026-09-17T10:00:00Z", "ERROR", "DatabaseConnectionException",
                "Connection acquisition timeout", "CORR-1", "database", "Dao", null);
        LogSearchResult mockResult = new LogSearchResult("payment-service", "local", 1, false, List.of(entry), List.of());
        when(logSearchService.searchLogs(eq("payment-service"), eq("local"), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(mockResult);

        SearchApplicationErrorsRequest request = new SearchApplicationErrorsRequest("payment-service", "local", 15, "timeout");
        ToolExecutionResult<LogSearchResult> result = searchErrorsTool.apply(request);

        assertThat(result.success()).isTrue();
        assertThat(result.data()).isNotNull();
        assertThat(result.data().totalHits()).isEqualTo(1);
        verify(auditor).audit(argThat(a -> a.toolName().equals(SearchApplicationErrorsAiTool.TOOL_NAME) && a.success()));
    }

    @Test
    @DisplayName("get_error_pattern_summary retrieves error categories and records audit")
    void testGetErrorPatternSummary() {
        ErrorPatternResult mockResult = new ErrorPatternResult("payment-service", "local", 15,
                List.of(new ErrorPatternDto("DatabaseConnectionException", 5L)), List.of());
        when(errorPatternService.summarizeErrors(eq("payment-service"), eq("local"), eq(15), anyInt()))
                .thenReturn(mockResult);

        ErrorPatternSummaryRequest request = new ErrorPatternSummaryRequest("payment-service", "local", 15);
        ToolExecutionResult<ErrorPatternResult> result = errorPatternTool.apply(request);

        assertThat(result.success()).isTrue();
        assertThat(result.data().patterns()).hasSize(1);
        assertThat(result.data().patterns().getFirst().count()).isEqualTo(5L);
        verify(auditor).audit(argThat(a -> a.toolName().equals(GetErrorPatternSummaryAiTool.TOOL_NAME) && a.success()));
    }

    @Test
    @DisplayName("get_application_log_timeline retrieves chronological timeline and records audit")
    void testGetApplicationLogTimeline() {
        LogTimelineResult mockResult = new LogTimelineResult("payment-service", "local",
                List.of(new LogTimelineEventDto("2026-09-17T10:00:00Z", "ERROR", "db", "Timeout", "Timed out", "CORR-1")),
                List.of());
        when(logTimelineService.getTimeline(eq("payment-service"), eq("local"), any(), any(), eq("CORR-1"), anyInt()))
                .thenReturn(mockResult);

        ApplicationLogTimelineRequest request = new ApplicationLogTimelineRequest("payment-service", "local", 15, "CORR-1");
        ToolExecutionResult<LogTimelineResult> result = timelineTool.apply(request);

        assertThat(result.success()).isTrue();
        assertThat(result.data().events()).hasSize(1);
        verify(auditor).audit(argThat(a -> a.toolName().equals(GetApplicationLogTimelineAiTool.TOOL_NAME) && a.success()));
    }

    @Test
    @DisplayName("AI tools validate application and environment inputs")
    void testParameterValidation() {
        SearchApplicationErrorsRequest invalidRequest = new SearchApplicationErrorsRequest("", "local", 15, null);
        ToolExecutionResult<LogSearchResult> result = searchErrorsTool.apply(invalidRequest);

        assertThat(result.success()).isFalse();
        assertThat(result.warning()).contains("must not be blank");
        verify(auditor).audit(argThat(a -> !a.success()));
    }
}
