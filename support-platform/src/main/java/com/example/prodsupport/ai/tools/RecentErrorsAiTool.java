package com.example.prodsupport.ai.tools;

import com.example.prodsupport.ai.tools.audit.ToolExecutionAudit;
import com.example.prodsupport.ai.tools.audit.ToolExecutionAuditor;
import com.example.prodsupport.ai.tools.context.InvestigationContext;
import com.example.prodsupport.ai.tools.context.InvestigationContextHolder;
import com.example.prodsupport.ai.tools.model.RecentErrorsData;
import com.example.prodsupport.ai.tools.model.RecentErrorsRequest;
import com.example.prodsupport.ai.tools.model.SanitizedErrorDto;
import com.example.prodsupport.ai.tools.model.ToolExecutionResult;
import com.example.prodsupport.ai.tools.security.ApplicationAccessValidator;
import com.example.prodsupport.ai.tools.security.ToolAllowlist;
import com.example.prodsupport.application.service.DiagnosticService;
import com.example.prodsupport.domain.RegisteredApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;

@Component
public class RecentErrorsAiTool implements Function<RecentErrorsRequest, ToolExecutionResult<RecentErrorsData>> {

    private static final Logger log = LoggerFactory.getLogger(RecentErrorsAiTool.class);

    public static final String TOOL_NAME = ToolAllowlist.TOOL_GET_RECENT_ERRORS;
    public static final String TOOL_DESCRIPTION = "Retrieve recent recorded error diagnostics and exceptions from the application. " +
            "Use this tool when investigating errors, failures, exceptions, or unexpected application behavior.";

    public static final int DEFAULT_LIMIT = 10;
    public static final int MIN_LIMIT = 1;
    public static final int MAX_LIMIT = 50;

    private final ApplicationAccessValidator accessValidator;
    private final DiagnosticService diagnosticService;
    private final ToolExecutionAuditor auditor;

    public RecentErrorsAiTool(ApplicationAccessValidator accessValidator,
                             DiagnosticService diagnosticService,
                             ToolExecutionAuditor auditor) {
        this.accessValidator = accessValidator;
        this.diagnosticService = diagnosticService;
        this.auditor = auditor;
    }

    @Override
    public ToolExecutionResult<RecentErrorsData> apply(RecentErrorsRequest request) {
        Instant startTime = Instant.now();
        InvestigationContext context = InvestigationContextHolder.getContext();

        if (context != null) {
            context.recordToolRequested(TOOL_NAME);
            if (!context.canExecuteTool()) {
                String warning = "Investigation reached maximum diagnostic steps. Answer is based on currently available evidence.";
                log.warn("Maximum tool call limit reached ({} calls). Aborting {}", context.getMaxToolCalls(), TOOL_NAME);
                context.recordWarning(warning);
                ToolExecutionResult<RecentErrorsData> limitResult = ToolExecutionResult.failure(TOOL_NAME, warning, startTime, 0);
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
            ToolExecutionResult<RecentErrorsData> invalidResult = ToolExecutionResult.failure(TOOL_NAME, warning, startTime, 0);
            if (context != null) {
                context.recordToolExecution(invalidResult);
            }
            return invalidResult;
        }

        int limit = request.limit() != null ? request.limit() : DEFAULT_LIMIT;
        if (limit < MIN_LIMIT || limit > MAX_LIMIT) {
            String warning = "Invalid limit: " + limit + " (allowed range is " + MIN_LIMIT + " to " + MAX_LIMIT + ")";
            auditor.audit(new ToolExecutionAudit(TOOL_NAME, request.applicationName(), request.environment(),
                    startTime, Instant.now(), 0, false, warning));
            ToolExecutionResult<RecentErrorsData> invalidLimitResult = ToolExecutionResult.failure(TOOL_NAME, warning, startTime, 0);
            if (context != null) {
                context.recordToolExecution(invalidLimitResult);
            }
            return invalidLimitResult;
        }

        RegisteredApplication app;
        try {
            app = accessValidator.validateAndGet(request.applicationName(), request.environment());
        } catch (Exception ex) {
            long duration = Duration.between(startTime, Instant.now()).toMillis();
            String failureReason = ex.getMessage();
            auditor.audit(new ToolExecutionAudit(TOOL_NAME, request.applicationName(), request.environment(),
                    startTime, Instant.now(), duration, false, failureReason));
            ToolExecutionResult<RecentErrorsData> authResult = ToolExecutionResult.failure(TOOL_NAME, failureReason, startTime, duration);
            if (context != null) {
                context.recordToolExecution(authResult);
            }
            return authResult;
        }

        try {
            RecentErrorsData data = diagnosticService.getRecentErrors(app, limit);
            long duration = Duration.between(startTime, Instant.now()).toMillis();
            auditor.audit(new ToolExecutionAudit(TOOL_NAME, app.getApplicationName(), app.getEnvironment(),
                    startTime, Instant.now(), duration, true, null));
            ToolExecutionResult<RecentErrorsData> successResult = ToolExecutionResult.success(TOOL_NAME, data, startTime, duration);
            if (context != null) {
                context.recordToolExecution(successResult);
                if (data.count() == 0) {
                    context.recordEvidence("No recent application errors recorded (count=0)");
                } else {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Found ").append(data.count()).append(" recent errors: ");
                    for (int i = 0; i < Math.min(3, data.errors().size()); i++) {
                        SanitizedErrorDto err = data.errors().get(i);
                        if (i > 0) sb.append("; ");
                        sb.append("[").append(err.type()).append(": ").append(err.message()).append("]");
                    }
                    context.recordEvidence(sb.toString());
                }
            }
            return successResult;
        } catch (Exception ex) {
            long duration = Duration.between(startTime, Instant.now()).toMillis();
            String errorMsg = diagnosticService.cleanErrorMessage(ex);
            String warning = "Unable to retrieve recent errors (" + errorMsg + ")";
            auditor.audit(new ToolExecutionAudit(TOOL_NAME, app.getApplicationName(), app.getEnvironment(),
                    startTime, Instant.now(), duration, false, errorMsg));
            ToolExecutionResult<RecentErrorsData> failResult = ToolExecutionResult.failure(TOOL_NAME, warning, startTime, duration);
            if (context != null) {
                context.recordToolExecution(failResult);
            }
            return failResult;
        }
    }
}
