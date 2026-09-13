package com.example.prodsupport.ai.tools;

import com.example.prodsupport.ai.tools.audit.ToolExecutionAudit;
import com.example.prodsupport.ai.tools.audit.ToolExecutionAuditor;
import com.example.prodsupport.ai.tools.context.InvestigationContext;
import com.example.prodsupport.ai.tools.context.InvestigationContextHolder;
import com.example.prodsupport.ai.tools.model.ApplicationHealthData;
import com.example.prodsupport.ai.tools.model.ApplicationHealthRequest;
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
public class HealthAiTool implements Function<ApplicationHealthRequest, ToolExecutionResult<ApplicationHealthData>> {

    private static final Logger log = LoggerFactory.getLogger(HealthAiTool.class);

    public static final String TOOL_NAME = ToolAllowlist.TOOL_CHECK_APPLICATION_HEALTH;
    public static final String TOOL_DESCRIPTION = "Retrieve current application health and actuator status. " +
            "Use this tool when the user asks whether an application is running, healthy, available, UP, DOWN, or unavailable.";

    private final ApplicationAccessValidator accessValidator;
    private final DiagnosticService diagnosticService;
    private final ToolExecutionAuditor auditor;

    public HealthAiTool(ApplicationAccessValidator accessValidator,
                        DiagnosticService diagnosticService,
                        ToolExecutionAuditor auditor) {
        this.accessValidator = accessValidator;
        this.diagnosticService = diagnosticService;
        this.auditor = auditor;
    }

    @Override
    public ToolExecutionResult<ApplicationHealthData> apply(ApplicationHealthRequest request) {
        Instant startTime = Instant.now();
        InvestigationContext context = InvestigationContextHolder.getContext();

        if (context != null) {
            context.recordToolRequested(TOOL_NAME);
            if (!context.canExecuteTool()) {
                String warning = "Investigation reached maximum diagnostic steps. Answer is based on currently available evidence.";
                log.warn("Maximum tool call limit reached ({} calls). Aborting {}", context.getMaxToolCalls(), TOOL_NAME);
                context.recordWarning(warning);
                ToolExecutionResult<ApplicationHealthData> limitResult = ToolExecutionResult.failure(TOOL_NAME, warning, startTime, 0);
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
            ToolExecutionResult<ApplicationHealthData> invalidResult = ToolExecutionResult.failure(TOOL_NAME, warning, startTime, 0);
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
            ToolExecutionResult<ApplicationHealthData> authResult = ToolExecutionResult.failure(TOOL_NAME, failureReason, startTime, duration);
            if (context != null) {
                context.recordToolExecution(authResult);
            }
            return authResult;
        }

        try {
            ApplicationHealthData data = diagnosticService.checkHealth(app);
            long duration = Duration.between(startTime, Instant.now()).toMillis();
            auditor.audit(new ToolExecutionAudit(TOOL_NAME, app.getApplicationName(), app.getEnvironment(),
                    startTime, Instant.now(), duration, true, null));
            ToolExecutionResult<ApplicationHealthData> successResult = ToolExecutionResult.success(TOOL_NAME, data, startTime, duration);
            if (context != null) {
                context.recordToolExecution(successResult);
                context.recordEvidence("Application health status is " + data.status() + " (source: " + data.source() + ")");
            }
            return successResult;
        } catch (Exception ex) {
            long duration = Duration.between(startTime, Instant.now()).toMillis();
            String errorMsg = diagnosticService.cleanErrorMessage(ex);
            String warning = "Unable to retrieve application health (" + errorMsg + ")";
            auditor.audit(new ToolExecutionAudit(TOOL_NAME, app.getApplicationName(), app.getEnvironment(),
                    startTime, Instant.now(), duration, false, errorMsg));
            ToolExecutionResult<ApplicationHealthData> failResult = ToolExecutionResult.failure(TOOL_NAME, warning, startTime, duration);
            if (context != null) {
                context.recordToolExecution(failResult);
            }
            return failResult;
        }
    }
}
