package com.example.prodsupport.tracing.evidence;

import com.example.prodsupport.tracing.dto.SpanDetailDto;
import com.example.prodsupport.tracing.dto.TraceSummaryDto;
import com.example.prodsupport.tracing.dto.TraceTimelineEventDto;
import com.example.prodsupport.tracing.model.SlowSpanAnalysisResult;
import com.example.prodsupport.tracing.model.TraceDetailResult;
import com.example.prodsupport.tracing.model.TraceSearchResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TraceEvidenceMapper {

    public String buildTraceSearchEvidenceSummary(String appName, String env, int minutes, TraceSearchResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== DISTRIBUTED TRACE SEARCH EVIDENCE (JAEGER) ===\n");
        sb.append("Application: ").append(appName).append("\n");
        sb.append("Environment: ").append(env).append("\n");
        sb.append("Time Window: Last ").append(minutes).append(" minutes\n");

        if (result == null || result.traces().isEmpty()) {
            sb.append("No distributed traces recorded in Jaeger within the query window.\n");
        } else {
            sb.append("Total Traces Found: ").append(result.traces().size()).append("\n");
            for (TraceSummaryDto t : result.traces()) {
                sb.append("  - [").append(t.traceId()).append("] Root: ").append(t.rootService())
                        .append(" -> ").append(t.operation())
                        .append(" (duration=").append(t.durationMs()).append("ms, spans=").append(t.spanCount())
                        .append(t.hasError() ? ", STATUS=ERROR" : ", STATUS=OK")
                        .append(", started=").append(t.startedAt()).append(")\n");
            }
        }

        if (result != null && !result.warnings().isEmpty()) {
            sb.append("Tracing Warnings:\n");
            for (String w : result.warnings()) {
                sb.append("  - ").append(w).append("\n");
            }
        }

        return sb.toString();
    }

    public String buildTraceDetailEvidenceSummary(TraceDetailResult detail) {
        if (detail == null) {
            return "No trace detail available.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== DISTRIBUTED TRACE DETAIL (TraceId: ").append(detail.traceId()).append(") ===\n");
        sb.append("Total Duration: ").append(detail.durationMs()).append(" ms\n");
        sb.append("Status: ").append(detail.hasError() ? "ERROR" : "OK").append("\n");
        sb.append("Participating Services: ").append(String.join(", ", detail.participatingServices())).append("\n");
        sb.append("Recorded Spans:\n");

        for (SpanDetailDto s : detail.spans()) {
            sb.append("  - [Offset +").append(s.startOffsetMs()).append("ms] ")
                    .append(s.service()).append(" : ").append(s.operation())
                    .append(" (duration=").append(s.durationMs()).append("ms, status=").append(s.status()).append(")");
            if ("ERROR".equalsIgnoreCase(s.status()) && s.sanitizedErrorDescription() != null) {
                sb.append(" [Error: ").append(s.sanitizedErrorDescription()).append("]");
            }
            sb.append("\n");
        }

        if (!detail.warnings().isEmpty()) {
            sb.append("Warnings:\n");
            for (String w : detail.warnings()) {
                sb.append("  - ").append(w).append("\n");
            }
        }

        return sb.toString();
    }

    public String buildSlowSpanEvidenceSummary(SlowSpanAnalysisResult slowResult) {
        if (slowResult == null) {
            return "No slow span analysis available.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== SLOW SPAN TIMING ANALYSIS (TraceId: ").append(slowResult.traceId()).append(") ===\n");
        sb.append("Total Trace Duration: ").append(slowResult.totalDurationMs()).append(" ms\n");

        if (slowResult.longestSpan() != null) {
            SpanDetailDto ls = slowResult.longestSpan();
            sb.append("Longest Measured Span: [").append(ls.service()).append("] ").append(ls.operation())
                    .append(" = ").append(ls.durationMs()).append(" ms (offset=+").append(ls.startOffsetMs()).append("ms)\n");
        }

        if (!slowResult.slowSpans().isEmpty()) {
            sb.append("Significant Duration Contributors:\n");
            for (SpanDetailDto s : slowResult.slowSpans()) {
                sb.append("  - ").append(s.service()).append(" : ").append(s.operation())
                        .append(" (duration=").append(s.durationMs()).append(" ms, status=").append(s.status()).append(")\n");
            }
        } else {
            sb.append("No individual spans exceeded significance thresholds.\n");
        }

        sb.append("Analysis Guidance: Measured span durations indicate where time was spent during the request. Concurrent child operations must not be summed together as sequential latency. Timing measurements alone do not prove root causation.\n");

        return sb.toString();
    }

    public Map<String, Object> toEvidenceItem(String type, String traceId, String service, String operation, long durationMs) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("source", "JAEGER");
        map.put("type", type);
        map.put("traceId", traceId);
        map.put("service", service);
        map.put("operation", operation);
        map.put("durationMs", durationMs);
        map.put("collectedAt", Instant.now().toString());
        return map;
    }
}
