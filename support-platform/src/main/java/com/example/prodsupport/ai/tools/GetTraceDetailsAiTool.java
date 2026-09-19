package com.example.prodsupport.ai.tools;

import com.example.prodsupport.ai.tools.audit.ToolExecutionAudit;
import com.example.prodsupport.ai.tools.audit.ToolExecutionAuditor;
import com.example.prodsupport.ai.tools.context.InvestigationContext;
import com.example.prodsupport.ai.tools.context.InvestigationContextHolder;
import com.example.prodsupport.ai.tools.model.GetTraceDetailsRequest;
import com.example.prodsupport.ai.tools.model.ToolExecutionResult;
import com.example.prodsupport.ai.tools.security.ApplicationAccessValidator;
import com.example.prodsupport.ai.tools.security.ToolAllowlist;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.tracing.evidence.TraceEvidenceMapper;
import com.example.prodsupport.tracing.model.TraceDetailResult;
import com.example.prodsupport.tracing.service.TraceSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;
import java.util.regex.Pattern;

@Component
public class GetTraceDetailsAiTool implements Function<GetTraceDetailsRequest, ToolExecutionResult<TraceDetailResult>> {

    private static final Logger log = LoggerFactory.getLogger(GetTraceDetailsAiTool.class);
    private static final Pattern VALID_TRACE_ID_PATTERN = Pattern.compile("^[0-9a-fA-F]{16,32}$");

    public static final String TOOL_NAME = ToolAllowlist.TOOL_GET_TRACE_DETAILS;
    public static final String TOOL_DESCRIPTION = "Retrieve sanitized spans for a trace belonging to an approved application investigation scope. " +
            "Use this when a trace ID is available and the user asks about request flow, errors, or latency. " +
            "Returns measured timings, participating services, and operations.";

    private final ApplicationAccessValidator accessValidator;
    private final TraceSearchService traceSearchService;
    private final TraceEvidenceMapper evidenceMapper;
    private final ToolExecutionAuditor auditor;

    public GetTraceDetailsAiTool(ApplicationAccessValidator accessValidator,
                                 TraceSearchService traceSearchService,
                                 TraceEvidenceMapper evidenceMapper,
                                 ToolExecutionAuditor auditor) {
        this.accessValidator = accessValidator;
        this.traceSearchService = traceSearchService;
        this.evidenceMapper = evidenceMapper;
        this.auditor = auditor;
    }

    @Override
    public ToolExecutionResult<TraceDetailResult> apply(GetTraceDetailsRequest request) {
        Instant startTime = Instant.now();
        InvestigationContext context = InvestigationContextHolder.getContext();

        if (context != null) {
            context.recordToolRequested(TOOL_NAME);
            if (!context.canExecuteTool()) {
                String warning = "Investigation reached maximum diagnostic steps. Answer is based on currently available evidence.";
                log.warn("Maximum tool call limit reached ({} calls). Aborting {}", context.getMaxToolCalls(), TOOL_NAME);
                context.recordWarning(warning);
                ToolExecutionResult<TraceDetailResult> limitResult = ToolExecutionResult.failure(TOOL_NAME, warning, startTime, 0);
                context.recordToolExecution(limitResult);
                return limitResult;
            }
            context.incrementExecutionCount();
        }

        if (request == null || request.applicationName() == null || request.environment() == null || request.traceId() == null) {
            String errorMsg = "applicationName, environment, and traceId are required parameters";
            ToolExecutionResult<TraceDetailResult> badResult = ToolExecutionResult.failure(TOOL_NAME, errorMsg, startTime, 0);
            if (context != null) {
                context.recordToolExecution(badResult);
            }
            return badResult;
        }

        String traceId = request.traceId().trim();
        if (!VALID_TRACE_ID_PATTERN.matcher(traceId).matches()) {
            String errorMsg = "Invalid traceId format: '" + traceId + "'. Must be 16 to 32 hex characters.";
            ToolExecutionResult<TraceDetailResult> badIdResult = ToolExecutionResult.failure(TOOL_NAME, errorMsg, startTime, 0);
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
            ToolExecutionResult<TraceDetailResult> authResult = ToolExecutionResult.failure(TOOL_NAME, failureReason, startTime, duration);
            if (context != null) {
                context.recordToolExecution(authResult);
            }
            return authResult;
        }

        try {
            TraceDetailResult result = traceSearchService.getTraceDetails(
                    app.getApplicationName(),
                    app.getEnvironment(),
                    traceId
            );

            long duration = Duration.between(startTime, Instant.now()).toMillis();
            auditor.audit(new ToolExecutionAudit(TOOL_NAME, app.getApplicationName(), app.getEnvironment(),
                    startTime, Instant.now(), duration, true, null));
            ToolExecutionResult<TraceDetailResult> successResult = ToolExecutionResult.success(TOOL_NAME, result, startTime, duration);

            if (context != null) {
                context.recordToolExecution(successResult);
                String summary = evidenceMapper.buildTraceDetailEvidenceSummary(result);
                context.recordEvidence(summary);
                context.putEvidence("traceDetail_" + traceId, result);
                if (result.warnings() != null) {
                    for (String w : result.warnings()) {
                        context.recordWarning(w);
                    }
                }
            }

            return successResult;

        } catch (Exception ex) {
            long duration = Duration.between(startTime, Instant.now()).toMillis();
            String failureReason = "Trace detail retrieval failed: " + ex.getMessage();
            log.warn("Trace detail retrieval failed for '{}/{}' trace '{}': {}",
                    app.getApplicationName(), app.getEnvironment(), traceId, failureReason);
            auditor.audit(new ToolExecutionAudit(TOOL_NAME, app.getApplicationName(), app.getEnvironment(),
                    startTime, Instant.now(), duration, false, failureReason));
            ToolExecutionResult<TraceDetailResult> failResult = ToolExecutionResult.failure(TOOL_NAME, failureReason, startTime, duration);
            if (context != null) {
                context.recordToolExecution(failResult);
                context.recordWarning(failureReason);
            }
            return failResult;
        }
    }
}
