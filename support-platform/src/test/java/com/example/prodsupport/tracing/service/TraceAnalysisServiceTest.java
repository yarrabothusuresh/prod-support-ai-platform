package com.example.prodsupport.tracing.service;

import com.example.prodsupport.tracing.dto.SpanDetailDto;
import com.example.prodsupport.tracing.dto.TraceTimelineEventDto;
import com.example.prodsupport.tracing.model.SlowSpanAnalysisResult;
import com.example.prodsupport.tracing.model.TraceDetailResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TraceAnalysisService Unit Tests")
class TraceAnalysisServiceTest {

    private TraceAnalysisService traceAnalysisService;

    @BeforeEach
    void setUp() {
        traceAnalysisService = new TraceAnalysisService();
    }

    @Test
    @DisplayName("Should detect slow spans exceeding threshold and identify longest span")
    void testAnalyzeSlowSpans() {
        SpanDetailDto root = new SpanDetailDto(
                "root", null, "payment-service", "POST /api/payments",
                2500L, 0L, "OK", null, null, Map.of("http.method", "POST")
        );
        SpanDetailDto fastChild = new SpanDetailDto(
                "child-fast", "root", "payment-service", "payment.validate",
                50L, 10L, "OK", null, null, Collections.emptyMap()
        );
        SpanDetailDto slowChild = new SpanDetailDto(
                "child-slow", "root", "payment-service", "payment.persist",
                2000L, 100L, "OK", null, null, Collections.emptyMap()
        );

        TraceDetailResult detail = new TraceDetailResult(
                "trace-slow-1", 2500L, false,
                List.of(root, fastChild, slowChild),
                Set.of("payment-service"),
                List.of()
        );

        SlowSpanAnalysisResult result = traceAnalysisService.analyzeSlowSpans(detail);

        assertThat(result.traceId()).isEqualTo("trace-slow-1");
        assertThat(result.totalDurationMs()).isEqualTo(2500L);
        assertThat(result.longestSpan()).isNotNull();
        assertThat(result.longestSpan().operation()).isEqualTo("POST /api/payments");
        assertThat(result.longestSpan().durationMs()).isEqualTo(2500L);
        assertThat(result.slowSpans()).hasSize(2);
        assertThat(result.slowSpans().get(0).operation()).isEqualTo("POST /api/payments");
        assertThat(result.slowSpans().get(1).operation()).isEqualTo("payment.persist");
    }

    @Test
    @DisplayName("Should extract error spans correctly")
    void testExtractErrorSpans() {
        SpanDetailDto okSpan = new SpanDetailDto(
                "span-ok", null, "payment-service", "payment.validate",
                100L, 0L, "OK", null, null, Collections.emptyMap()
        );
        SpanDetailDto errSpan = new SpanDetailDto(
                "span-err", null, "payment-service", "payment.persist",
                150L, 100L, "ERROR", "DatabaseException", "Simulated database failure",
                Map.of("error", "true")
        );

        TraceDetailResult detail = new TraceDetailResult(
                "trace-err-1", 250L, true,
                List.of(okSpan, errSpan),
                Set.of("payment-service"),
                List.of()
        );

        List<SpanDetailDto> errorSpans = traceAnalysisService.findErrorSpans(detail);

        assertThat(errorSpans).hasSize(1);
        assertThat(errorSpans.get(0).spanId()).isEqualTo("span-err");
        assertThat(errorSpans.get(0).sanitizedErrorDescription()).isEqualTo("Simulated database failure");
    }

    @Test
    @DisplayName("Should build chronological timeline correctly ordered by start offset")
    void testBuildTimeline() {
        SpanDetailDto root = new SpanDetailDto(
                "root", null, "payment-service", "POST /api/payments",
                1000L, 0L, "OK", null, null, Collections.emptyMap()
        );
        SpanDetailDto child1 = new SpanDetailDto(
                "child-1", "root", "payment-service", "payment.validate",
                200L, 100L, "OK", null, null, Collections.emptyMap()
        );
        SpanDetailDto grandChild = new SpanDetailDto(
                "grandchild-1", "child-1", "payment-service", "validate.rules",
                50L, 120L, "OK", null, null, Collections.emptyMap()
        );

        TraceDetailResult detail = new TraceDetailResult(
                "trace-tree", 1000L, false,
                List.of(root, child1, grandChild),
                Set.of("payment-service"),
                List.of()
        );

        List<TraceTimelineEventDto> timeline = traceAnalysisService.buildChronologicalTimeline(detail);

        assertThat(timeline).hasSize(3);
        assertThat(timeline.get(0).offsetMs()).isEqualTo(0L);
        assertThat(timeline.get(0).service()).isEqualTo("payment-service");
        assertThat(timeline.get(0).operation()).isEqualTo("POST /api/payments");

        assertThat(timeline.get(1).offsetMs()).isEqualTo(100L);
        assertThat(timeline.get(1).operation()).isEqualTo("payment.validate");

        assertThat(timeline.get(2).offsetMs()).isEqualTo(120L);
        assertThat(timeline.get(2).operation()).isEqualTo("validate.rules");
    }
}
