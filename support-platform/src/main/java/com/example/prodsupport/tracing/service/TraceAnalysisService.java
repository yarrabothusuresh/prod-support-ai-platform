package com.example.prodsupport.tracing.service;

import com.example.prodsupport.tracing.dto.SpanDetailDto;
import com.example.prodsupport.tracing.dto.TraceTimelineEventDto;
import com.example.prodsupport.tracing.model.SlowSpanAnalysisResult;
import com.example.prodsupport.tracing.model.TraceDetailResult;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class TraceAnalysisService {

    private static final double SIGNIFICANT_LATENCY_RATIO = 0.30; // 30% or more of trace duration

    public SlowSpanAnalysisResult analyzeSlowSpans(TraceDetailResult detail) {
        if (detail == null || detail.spans().isEmpty()) {
            return new SlowSpanAnalysisResult(
                    detail != null ? detail.traceId() : "unknown",
                    detail != null ? detail.durationMs() : 0,
                    null,
                    List.of(),
                    detail != null ? detail.warnings() : List.of("No span data available for analysis.")
            );
        }

        List<SpanDetailDto> spans = detail.spans();
        SpanDetailDto longestSpan = null;
        long maxDuration = -1;

        for (SpanDetailDto s : spans) {
            if (s.durationMs() > maxDuration) {
                maxDuration = s.durationMs();
                longestSpan = s;
            }
        }

        List<SpanDetailDto> slowSpans = new ArrayList<>();
        long totalDuration = detail.durationMs();

        for (SpanDetailDto s : spans) {
            boolean isSlow = false;
            if (totalDuration > 0 && ((double) s.durationMs() / totalDuration) >= SIGNIFICANT_LATENCY_RATIO) {
                isSlow = true;
            } else if (s.durationMs() >= 500) {
                isSlow = true;
            }
            if (isSlow) {
                slowSpans.add(s);
            }
        }

        slowSpans.sort((a, b) -> Long.compare(b.durationMs(), a.durationMs()));

        List<String> warnings = new ArrayList<>(detail.warnings());
        warnings.add("Note: Long span durations represent observed timing measurements and do not automatically prove root causation. Concurrent operations must not be summed as sequential latency.");

        return new SlowSpanAnalysisResult(
                detail.traceId(),
                totalDuration,
                longestSpan,
                slowSpans,
                warnings
        );
    }

    public List<SpanDetailDto> findErrorSpans(TraceDetailResult detail) {
        if (detail == null || detail.spans().isEmpty()) {
            return List.of();
        }
        return detail.spans().stream()
                .filter(s -> "ERROR".equalsIgnoreCase(s.status()))
                .toList();
    }

    public List<TraceTimelineEventDto> buildChronologicalTimeline(TraceDetailResult detail) {
        if (detail == null || detail.spans().isEmpty()) {
            return List.of();
        }

        List<TraceTimelineEventDto> timeline = new ArrayList<>();
        List<SpanDetailDto> sortedSpans = new ArrayList<>(detail.spans());
        sortedSpans.sort(Comparator.comparingLong(SpanDetailDto::startOffsetMs));

        for (SpanDetailDto s : sortedSpans) {
            String desc = String.format("[%s] %s (%d ms)", s.service(), s.operation(), s.durationMs());
            if ("ERROR".equalsIgnoreCase(s.status())) {
                desc += " - FAILED: " + (s.sanitizedErrorDescription() != null ? s.sanitizedErrorDescription() : "Error status");
            }
            timeline.add(new TraceTimelineEventDto(
                    "+" + s.startOffsetMs() + "ms",
                    s.startOffsetMs(),
                    s.service(),
                    s.operation(),
                    desc,
                    s.status()
            ));
        }

        return timeline;
    }
}
