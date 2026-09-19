package com.example.prodsupport.ai.tools;

import com.example.prodsupport.ai.tools.audit.ToolExecutionAudit;
import com.example.prodsupport.ai.tools.audit.ToolExecutionAuditor;
import com.example.prodsupport.ai.tools.context.InvestigationContext;
import com.example.prodsupport.ai.tools.context.InvestigationContextHolder;
import com.example.prodsupport.ai.tools.model.AnalyzeSlowSpansRequest;
import com.example.prodsupport.ai.tools.model.ToolExecutionResult;
import com.example.prodsupport.ai.tools.security.ApplicationAccessValidator;
import com.example.prodsupport.ai.tools.security.ToolAllowlist;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.tracing.evidence.TraceEvidenceMapper;
import com.example.prodsupport.tracing.model.SlowSpanAnalysisResult;
import com.example.prodsupport.tracing.model.TraceDetailResult;
import com.example.prodsupport.tracing.service.TraceAnalysisService;
import com.example.prodsupport.tracing.service.TraceSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

@Component
public class AnalyzeSlowSpansAiTool implements Function<AnalyzeSlowSpansRequest, ToolExecutionResult<SlowSpanAnalysisResult>> {

    private static final Logger log = LoggerFactory.getLogger(AnalyzeSlowSpansAiTool.class);
    private static final Pattern VALID_TRACE_ID_PATTERN = Pattern.compile("^[0-9a-fA-F]{16,32}$");

    public static final String TOOL_NAME = ToolAllowlist.TOOL_ANALYZE_SLOW_SPANS;
    public static final String TOOL_DESCRIPTION = "Identify slow spans, long-duration spans and timing information from an approved distributed trace. " +
            "This tool reports measured timings and duration contributors, and does not automatically determine root cause.";

    private final ApplicationAccessValidator accessValidator;
    private final TraceSearchService traceSearchService;
    private final TraceAnalysisService traceAnalysisService;
    private final TraceEvidenceMapper evidenceMapper;
    private final ToolExecutionAuditor auditor;

    public AnalyzeSlowSpansAiTool(ApplicationAccessValidator accessValidator,
                                  TraceSearchService traceSearchService,
                                  TraceAnalysisService traceAnalysisService,
                                  TraceEvidenceMapper evidenceMapper,
                                  ToolExecutionAuditor auditor) {
        this.accessValidator = accessValidator;
        this.traceSearchService = traceSearchService;
        this.traceAnalysisService = traceAnalysisService;
        this.evidenceMapper = evidenceMapper;
        this.auditor = auditor;
    }

    @Override
    public ToolExecutionResult<SlowSpanAnalysisResult> apply(AnalyzeSlowSpansRequest request) {
        Instant startTime = Instant.now();
        InvestigationContext context = InvestigationContextHolder.getContext();

        if (context != null) {
            context.recordToolRequested(TOOL_NAME);
            if (!context.canExecuteTool()) {
                String warning = "Investigation reached maximum diagnostic steps. Answer is based on currently available evidence.";
                log.warn("Maximum tool call limit reached ({} calls). Aborting {}", context.getMaxToolCalls(), TOOL_NAME);
                context.recordWarning(warning);
                ToolExecutionResult<SlowSpanAnalysisResult> limitResult = ToolExecutionResult.failure(TOOL_NAME, warning, startTime, 0);
                context.recordToolExecution(limitResult);
                return limitResult;
            }
            context.incrementExecutionCount();
        }

        if (request == null || request.applicationName() == null || request.environment() == null || request.traceId() == null) {
            String errorMsg = "applicationName, environment, and traceId are required parameters";
            ToolExecutionResult<SlowSpanAnalysisResult> badResult = ToolExecutionResult.failure(TOOL_NAME, errorMsg, startTime, 0);
            if (context != null) {
                context.recordToolExecution(badResult);
            }
            return badResult;
        }

        String traceId = request.traceId().trim();
        if (!VALID_TRACE_ID_PATTERN.matcher(traceId).matches()) {
            String errorMsg = "Invalid traceId format: '" + traceId + "'. Must be 16 to 32 hex characters.";
            ToolExecutionResult<SlowSpanAnalysisResult> badIdResult = ToolExecutionResult.failure(TOOL_NAME, errorMsg, startTime, 0);
            if (context != null) {
                context.recordToolExecution(badIdResult);
            }
            return badIdResult;
        }

        RegisteredApplication app;
        try {
            app = accessValidator.validateAndGet(request.applicationName(), request.environment());
        } catch (Exception ex) {
            long duration = Duration.between(startTime, Instant.now()).toMillis();
            String failureReason = ex.getMessage();
            auditor.audit(new ToolExecutionAudit(TOOL_NAME, request.applicationName(), request.environment(),
                    startTime, Instant.now(), duration, false, failureReason));
            ToolExecutionResult<SlowSpanAnalysisResult> authResult = ToolExecutionResult.failure(TOOL_NAME, failureReason, startTime, duration);
            if (context != null) {
                context.recordToolExecution(authResult);
            }
            return authResult;
        }

        try {
            TraceDetailResult detail = traceSearchService.getTraceDetails(
                    app.getApplicationName(),
                    app.getEnvironment(),
                    traceId
            );

            SlowSpanAnalysisResult result = traceAnalysisService.analyzeSlowSpans(detail);

            long duration = Duration.between(startTime, Instant.now()).toMillis();
            auditor.audit(new ToolExecutionAudit(TOOL_NAME, app.getApplicationName(), app.getEnvironment(),
                    startTime, Instant.now(), duration, true, null));
            ToolExecutionResult<SlowSpanAnalysisResult> successResult = ToolExecutionResult.success(TOOL_NAME, result, startTime, duration);

            if (context != null) {
                context.recordToolExecution(successResult);
                String summary = evidenceMapper.buildSlowSpanEvidenceSummary(result);
                context.recordEvidence(summary);
                context.putEvidence("slowSpanAnalysis_" + traceId, result);
                if (result.warnings() != null) {
                    for (String w : result.warnings()) {
                        context.recordWarning(w);
                    }
                }
            }

            return successResult;

        } catch (Exception ex) {
            long duration = Duration.between(startTime, Instant.now()).toMillis();
            String failureReason = "Slow span analysis failed: " + ex.getMessage();
            log.warn("Slow span analysis failed for '{}/{}' trace '{}': {}",
                    app.getApplicationName(), app.getEnvironment(), traceId, failureReason);
            auditor.audit(new ToolExecutionAudit(TOOL_NAME, app.getApplicationName(), app.getEnvironment(),
                    startTime, Instant.now(), duration, false, failureReason));
            ToolExecutionResult<SlowSpanAnalysisResult> failResult = ToolExecutionResult.failure(TOOL_NAME, failureReason, startTime, duration);
            if (context != null) {
                context.recordToolExecution(failResult);
                context.recordWarning(failureReason);
            }
            return failResult;
        }
    }
}
