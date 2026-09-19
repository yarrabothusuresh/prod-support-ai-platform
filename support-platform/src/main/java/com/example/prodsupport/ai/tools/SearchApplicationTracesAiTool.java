package com.example.prodsupport.ai.tools;

import com.example.prodsupport.ai.tools.audit.ToolExecutionAudit;
import com.example.prodsupport.ai.tools.audit.ToolExecutionAuditor;
import com.example.prodsupport.ai.tools.context.InvestigationContext;
import com.example.prodsupport.ai.tools.context.InvestigationContextHolder;
import com.example.prodsupport.ai.tools.model.SearchApplicationTracesRequest;
import com.example.prodsupport.ai.tools.model.ToolExecutionResult;
import com.example.prodsupport.ai.tools.security.ApplicationAccessValidator;
import com.example.prodsupport.ai.tools.security.ToolAllowlist;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.tracing.evidence.TraceEvidenceMapper;
import com.example.prodsupport.tracing.model.TraceSearchResult;
import com.example.prodsupport.tracing.service.TraceSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;

@Component
public class SearchApplicationTracesAiTool implements Function<SearchApplicationTracesRequest, ToolExecutionResult<TraceSearchResult>> {

    private static final Logger log = LoggerFactory.getLogger(SearchApplicationTracesAiTool.class);

    public static final String TOOL_NAME = ToolAllowlist.TOOL_SEARCH_APPLICATION_TRACES;
    public static final String TOOL_DESCRIPTION = "Search recent distributed traces for a registered application. " +
            "Use this tool when investigating request latency, failed requests, or service-to-service communication issues. " +
            "Search is strictly restricted to the registered application's approved investigation scope.";

    private final ApplicationAccessValidator accessValidator;
    private final TraceSearchService traceSearchService;
    private final TraceEvidenceMapper evidenceMapper;
    private final ToolExecutionAuditor auditor;

    public SearchApplicationTracesAiTool(ApplicationAccessValidator accessValidator,
                                         TraceSearchService traceSearchService,
                                         TraceEvidenceMapper evidenceMapper,
                                         ToolExecutionAuditor auditor) {
        this.accessValidator = accessValidator;
        this.traceSearchService = traceSearchService;
        this.evidenceMapper = evidenceMapper;
        this.auditor = auditor;
    }

    @Override
    public ToolExecutionResult<TraceSearchResult> apply(SearchApplicationTracesRequest request) {
        Instant startTime = Instant.now();
        InvestigationContext context = InvestigationContextHolder.getContext();

        if (context != null) {
            context.recordToolRequested(TOOL_NAME);
            if (!context.canExecuteTool()) {
                String warning = "Investigation reached maximum diagnostic steps. Answer is based on currently available evidence.";
                log.warn("Maximum tool call limit reached ({} calls). Aborting {}", context.getMaxToolCalls(), TOOL_NAME);
                context.recordWarning(warning);
                ToolExecutionResult<TraceSearchResult> limitResult = ToolExecutionResult.failure(TOOL_NAME, warning, startTime, 0);
                context.recordToolExecution(limitResult);
                return limitResult;
            }
            context.incrementExecutionCount();
        }

        if (request == null || request.applicationName() == null || request.environment() == null) {
            String errorMsg = "applicationName and environment are required parameters";
            ToolExecutionResult<TraceSearchResult> badResult = ToolExecutionResult.failure(TOOL_NAME, errorMsg, startTime, 0);
            if (context != null) {
                context.recordToolExecution(badResult);
            }
            return badResult;
        }

        RegisteredApplication app;
        try {
            app = accessValidator.validateAndGet(request.applicationName(), request.environment());
        } catch (Exception ex) {
            long duration = Duration.between(startTime, Instant.now()).toMillis();
            String failureReason = ex.getMessage();
            auditor.audit(new ToolExecutionAudit(TOOL_NAME, request.applicationName(), request.environment(),
                    startTime, Instant.now(), duration, false, failureReason));
            ToolExecutionResult<TraceSearchResult> authResult = ToolExecutionResult.failure(TOOL_NAME, failureReason, startTime, duration);
            if (context != null) {
                context.recordToolExecution(authResult);
            }
            return authResult;
        }

        int minutes = request.minutes() != null && request.minutes() > 0 ? request.minutes() : 15;
        boolean errorOnly = request.errorOnly() != null && request.errorOnly();

        try {
            TraceSearchResult result = traceSearchService.searchTraces(
                    app.getApplicationName(),
                    app.getEnvironment(),
                    minutes,
                    20,
                    errorOnly
            );

            long duration = Duration.between(startTime, Instant.now()).toMillis();
            auditor.audit(new ToolExecutionAudit(TOOL_NAME, app.getApplicationName(), app.getEnvironment(),
                    startTime, Instant.now(), duration, true, null));
            ToolExecutionResult<TraceSearchResult> successResult = ToolExecutionResult.success(TOOL_NAME, result, startTime, duration);

            if (context != null) {
                context.recordToolExecution(successResult);
                String summary = evidenceMapper.buildTraceSearchEvidenceSummary(app.getApplicationName(), app.getEnvironment(), minutes, result);
                context.recordEvidence(summary);
                context.putEvidence("traceSearchResult", result);
                if (result.warnings() != null) {
                    for (String w : result.warnings()) {
                        context.recordWarning(w);
                    }
                }
            }

            return successResult;

        } catch (Exception ex) {
            long duration = Duration.between(startTime, Instant.now()).toMillis();
            String failureReason = "Tracing search failed: " + ex.getMessage();
            log.warn("Tracing search execution failed for '{}/{}': {}", app.getApplicationName(), app.getEnvironment(), failureReason);
            auditor.audit(new ToolExecutionAudit(TOOL_NAME, app.getApplicationName(), app.getEnvironment(),
                    startTime, Instant.now(), duration, false, failureReason));
            ToolExecutionResult<TraceSearchResult> failResult = ToolExecutionResult.failure(TOOL_NAME, failureReason, startTime, duration);
            if (context != null) {
                context.recordToolExecution(failResult);
                context.recordWarning(failureReason);
            }
            return failResult;
        }
    }
}
