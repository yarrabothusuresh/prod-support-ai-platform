package com.example.prodsupport.tracing.tools;

import com.example.prodsupport.ai.tools.AnalyzeSlowSpansAiTool;
import com.example.prodsupport.ai.tools.GetTraceDetailsAiTool;
import com.example.prodsupport.ai.tools.SearchApplicationTracesAiTool;
import com.example.prodsupport.ai.tools.audit.ToolExecutionAuditor;
import com.example.prodsupport.ai.tools.context.InvestigationContext;
import com.example.prodsupport.ai.tools.context.InvestigationContextHolder;
import com.example.prodsupport.ai.tools.model.AnalyzeSlowSpansRequest;
import com.example.prodsupport.ai.tools.model.GetTraceDetailsRequest;
import com.example.prodsupport.ai.tools.model.SearchApplicationTracesRequest;
import com.example.prodsupport.ai.tools.model.ToolExecutionResult;
import com.example.prodsupport.ai.tools.security.ApplicationAccessValidator;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.tracing.dto.SpanDetailDto;
import com.example.prodsupport.tracing.dto.TraceSummaryDto;
import com.example.prodsupport.tracing.evidence.TraceEvidenceMapper;
import com.example.prodsupport.tracing.model.SlowSpanAnalysisResult;
import com.example.prodsupport.tracing.model.TraceDetailResult;
import com.example.prodsupport.tracing.model.TraceSearchResult;
import com.example.prodsupport.tracing.service.TraceAnalysisService;
import com.example.prodsupport.tracing.service.TraceSearchService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Tracing AI Tools Unit Tests")
class TracingAiToolsTest {

    @Mock
    private ApplicationAccessValidator accessValidator;

    @Mock
    private TraceSearchService traceSearchService;

    @Mock
    private TraceAnalysisService traceAnalysisService;

    @Mock
    private TraceEvidenceMapper evidenceMapper;

    @Mock
    private ToolExecutionAuditor auditor;

    private SearchApplicationTracesAiTool searchTracesTool;
    private GetTraceDetailsAiTool getTraceDetailsTool;
    private AnalyzeSlowSpansAiTool analyzeSlowSpansTool;

    private RegisteredApplication testApp;

    @BeforeEach
    void setUp() {
        searchTracesTool = new SearchApplicationTracesAiTool(accessValidator, traceSearchService, evidenceMapper, auditor);
        getTraceDetailsTool = new GetTraceDetailsAiTool(accessValidator, traceSearchService, evidenceMapper, auditor);
        analyzeSlowSpansTool = new AnalyzeSlowSpansAiTool(accessValidator, traceSearchService, traceAnalysisService, evidenceMapper, auditor);

        testApp = new RegisteredApplication("payment-service", "payments", "local",
                "Payment service", "http://localhost:8081", "http://localhost:8081/actuator/health",
                "http://localhost:8081/support/info", true);

        InvestigationContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        InvestigationContextHolder.clearContext();
    }

    @Test
    @DisplayName("SearchApplicationTracesAiTool should execute successfully and record evidence in InvestigationContext")
    void testSearchTracesSuccess() {
        when(accessValidator.validateAndGet("payment-service", "local")).thenReturn(testApp);

        TraceSummaryDto summary = new TraceSummaryDto(
                "trace-1", "payment-service", "POST /api/payments",
                120L, false, 2, "2026-09-19T08:00:00Z"
        );
        TraceSearchResult searchResult = new TraceSearchResult(
                "payment-service", "local", List.of(summary), Collections.emptyList()
        );
        when(traceSearchService.searchTraces(eq("payment-service"), eq("local"), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(searchResult);

        InvestigationContext context = new InvestigationContext("payment-service", "local", "Test question", 10);
        InvestigationContextHolder.setContext(context);

        SearchApplicationTracesRequest request = new SearchApplicationTracesRequest(
                "payment-service", "local", 15, false
        );

        ToolExecutionResult<TraceSearchResult> result = searchTracesTool.apply(request);

        assertThat(result.success()).isTrue();
        assertThat(result.data()).isNotNull();
        assertThat(result.data().traces()).hasSize(1);
        verify(evidenceMapper).buildTraceSearchEvidenceSummary(eq("payment-service"), eq("local"), anyInt(), eq(searchResult));
        verify(auditor).audit(any());
        assertThat(context.getExecutionCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("GetTraceDetailsAiTool should validate trace ID format and reject invalid format")
    void testGetTraceDetailsInvalidFormat() {
        GetTraceDetailsRequest request = new GetTraceDetailsRequest(
                "payment-service", "local", "invalid-trace-id!"
        );

        ToolExecutionResult<TraceDetailResult> result = getTraceDetailsTool.apply(request);

        assertThat(result.success()).isFalse();
        assertThat(result.warning()).containsIgnoringCase("Invalid trace");
        verifyNoInteractions(traceSearchService);
    }

    @Test
    @DisplayName("GetTraceDetailsAiTool should return trace details when valid")
    void testGetTraceDetailsSuccess() {
        when(accessValidator.validateAndGet("payment-service", "local")).thenReturn(testApp);

        SpanDetailDto span = new SpanDetailDto(
                "span-1", null, "payment-service", "POST /api/payments",
                300L, 0L, "OK", null, null, Collections.emptyMap()
        );
        TraceDetailResult detail = new TraceDetailResult(
                "4bf92f3577b34da6a3ce929d0e0e4736", 300L, false,
                List.of(span), Set.of("payment-service"), List.of()
        );
        when(traceSearchService.getTraceDetails("payment-service", "local", "4bf92f3577b34da6a3ce929d0e0e4736"))
                .thenReturn(detail);

        GetTraceDetailsRequest request = new GetTraceDetailsRequest(
                "payment-service", "local", "4bf92f3577b34da6a3ce929d0e0e4736"
        );

        ToolExecutionResult<TraceDetailResult> result = getTraceDetailsTool.apply(request);

        assertThat(result.success()).isTrue();
        assertThat(result.data().traceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(result.data().spans()).hasSize(1);
    }

    @Test
    @DisplayName("AnalyzeSlowSpansAiTool should analyze trace and return slow span result")
    void testAnalyzeSlowSpansSuccess() {
        when(accessValidator.validateAndGet("payment-service", "local")).thenReturn(testApp);

        TraceDetailResult detail = new TraceDetailResult(
                "4bf92f3577b34da6a3ce929d0e0e4736", 2500L, false,
                Collections.emptyList(), Set.of("payment-service"), List.of()
        );
        when(traceSearchService.getTraceDetails("payment-service", "local", "4bf92f3577b34da6a3ce929d0e0e4736"))
                .thenReturn(detail);

        SlowSpanAnalysisResult slowSpanResult = new SlowSpanAnalysisResult(
                "4bf92f3577b34da6a3ce929d0e0e4736", 2500L, null, Collections.emptyList(), Collections.emptyList()
        );
        when(traceAnalysisService.analyzeSlowSpans(detail)).thenReturn(slowSpanResult);

        AnalyzeSlowSpansRequest request = new AnalyzeSlowSpansRequest(
                "payment-service", "local", "4bf92f3577b34da6a3ce929d0e0e4736"
        );

        ToolExecutionResult<SlowSpanAnalysisResult> result = analyzeSlowSpansTool.apply(request);

        assertThat(result.success()).isTrue();
        assertThat(result.data().traceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(result.data().totalDurationMs()).isEqualTo(2500L);
    }

    @Test
    @DisplayName("Tracing tools should abort when InvestigationContext reaches max tool calls")
    void testMaxToolCallsAbortsExecution() {
        InvestigationContext context = new InvestigationContext("payment-service", "local", "Test question", 1);
        context.incrementExecutionCount();
        InvestigationContextHolder.setContext(context);

        SearchApplicationTracesRequest request = new SearchApplicationTracesRequest(
                "payment-service", "local", 15, false
        );

        ToolExecutionResult<TraceSearchResult> result = searchTracesTool.apply(request);

        assertThat(result.success()).isFalse();
        assertThat(result.warning()).contains("maximum diagnostic steps");
        verifyNoInteractions(traceSearchService);
    }
}
