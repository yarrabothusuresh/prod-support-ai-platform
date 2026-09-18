package com.example.prodsupport.ai.tools;

import com.example.prodsupport.ai.tools.audit.ToolExecutionAudit;
import com.example.prodsupport.ai.tools.audit.ToolExecutionAuditor;
import com.example.prodsupport.ai.tools.context.InvestigationContext;
import com.example.prodsupport.ai.tools.context.InvestigationContextHolder;
import com.example.prodsupport.ai.tools.model.ErrorPatternSummaryRequest;
import com.example.prodsupport.ai.tools.model.ToolExecutionResult;
import com.example.prodsupport.ai.tools.security.ApplicationAccessValidator;
import com.example.prodsupport.ai.tools.security.ToolAllowlist;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.logging.model.ErrorPatternDto;
import com.example.prodsupport.logging.model.ErrorPatternResult;
import com.example.prodsupport.logging.service.ErrorPatternService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;

@Component
public class GetErrorPatternSummaryAiTool implements Function<ErrorPatternSummaryRequest, ToolExecutionResult<ErrorPatternResult>> {

    private static final Logger log = LoggerFactory.getLogger(GetErrorPatternSummaryAiTool.class);

    public static final String TOOL_NAME = ToolAllowlist.TOOL_GET_ERROR_PATTERN_SUMMARY;
    public static final String TOOL_DESCRIPTION = "Retrieve aggregated counts of recent application error categories. " +
            "Use this tool when identifying recurring exceptions, dominant error types or repeated application failures. " +
            "Results are based on Elasticsearch evidence.";

    private final ApplicationAccessValidator accessValidator;
    private final ErrorPatternService errorPatternService;
    private final ToolExecutionAuditor auditor;

    public GetErrorPatternSummaryAiTool(ApplicationAccessValidator accessValidator,
                                        ErrorPatternService errorPatternService,
                                        ToolExecutionAuditor auditor) {
        this.accessValidator = accessValidator;
        this.errorPatternService = errorPatternService;
        this.auditor = auditor;
    }

    @Override
    public ToolExecutionResult<ErrorPatternResult> apply(ErrorPatternSummaryRequest request) {
        Instant startTime = Instant.now();
        InvestigationContext context = InvestigationContextHolder.getContext();

        if (context != null) {
            context.recordToolRequested(TOOL_NAME);
            if (!context.canExecuteTool()) {
                String warning = "Investigation reached maximum diagnostic steps. Answer is based on currently available evidence.";
                log.warn("Maximum tool call limit reached ({} calls). Aborting {}", context.getMaxToolCalls(), TOOL_NAME);
                context.recordWarning(warning);
                ToolExecutionResult<ErrorPatternResult> limitResult = ToolExecutionResult.failure(TOOL_NAME, warning, startTime, 0);
                context.recordToolExecution(limitResult);
                return limitResult;
            }
            context.incrementExecutionCount();
        }

        if (request == null || request.applicationName() == null || request.applicationName().isBlank()
                || request.environment() == null || request.environment().isBlank()) {
            String warning = "Invalid parameters: applicationName and environment must not be blank";
            auditor.audit(new ToolExecutionAudit(TOOL_NAME, request != null ? request.applicationName() : "unknown",
                    request != null ? request.environment() : "unknown", startTime, Instant.now(), 0, false, warning));
            ToolExecutionResult<ErrorPatternResult> invalidResult = ToolExecutionResult.failure(TOOL_NAME, warning, startTime, 0);
            if (context != null) {
                context.recordToolExecution(invalidResult);
            }
            return invalidResult;
        }

        RegisteredApplication app;
        try {
            app = accessValidator.validateAndGet(request.applicationName(), request.environment());
        } catch (Exception ex) {
            long duration = Duration.between(startTime, Instant.now()).toMillis();
            String failureReason = ex.getMessage();
            auditor.audit(new ToolExecutionAudit(TOOL_NAME, request.applicationName(), request.environment(),
                    startTime, Instant.now(), duration, false, failureReason));
            ToolExecutionResult<ErrorPatternResult> authResult = ToolExecutionResult.failure(TOOL_NAME, failureReason, startTime, duration);
            if (context != null) {
                context.recordToolExecution(authResult);
            }
            return authResult;
        }

        int minutes = request.minutes() != null && request.minutes() > 0 ? request.minutes() : 15;

        try {
            ErrorPatternResult result = errorPatternService.summarizeErrors(
                    app.getApplicationName(),
                    app.getEnvironment(),
                    minutes,
                    10
            );

            long duration = Duration.between(startTime, Instant.now()).toMillis();
            auditor.audit(new ToolExecutionAudit(TOOL_NAME, app.getApplicationName(), app.getEnvironment(),
                    startTime, Instant.now(), duration, true, null));
            ToolExecutionResult<ErrorPatternResult> successResult = ToolExecutionResult.success(TOOL_NAME, result, startTime, duration);

            if (context != null) {
                context.recordToolExecution(successResult);
                if (result.patterns().isEmpty()) {
                    context.recordEvidence("Error pattern aggregation: No recurring error categories detected over last " + minutes + "m");
                } else {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Error patterns (last ").append(minutes).append("m): ");
                    for (int i = 0; i < result.patterns().size(); i++) {
                        ErrorPatternDto p = result.patterns().get(i);
                        if (i > 0) sb.append(", ");
                        sb.append(p.errorType()).append("=").append(p.count());
                    }
                    context.recordEvidence(sb.toString());
                }
            }

            return successResult;

        } catch (Exception ex) {
            long duration = Duration.between(startTime, Instant.now()).toMillis();
            String errorMsg = ex.getMessage();
            String warning = "Error pattern aggregation currently unavailable (" + errorMsg + ")";
            auditor.audit(new ToolExecutionAudit(TOOL_NAME, app.getApplicationName(), app.getEnvironment(),
                    startTime, Instant.now(), duration, false, errorMsg));
            ToolExecutionResult<ErrorPatternResult> failResult = ToolExecutionResult.failure(TOOL_NAME, warning, startTime, duration);
            if (context != null) {
                context.recordToolExecution(failResult);
                context.recordWarning(warning);
            }
            return failResult;
        }
    }
}
