package com.example.prodsupport.ai.tools;

import com.example.prodsupport.ai.tools.audit.ToolExecutionAudit;
import com.example.prodsupport.ai.tools.audit.ToolExecutionAuditor;
import com.example.prodsupport.ai.tools.context.InvestigationContext;
import com.example.prodsupport.ai.tools.context.InvestigationContextHolder;
import com.example.prodsupport.ai.tools.model.DatabaseDiagnosticToolRequest;
import com.example.prodsupport.ai.tools.model.ToolExecutionResult;
import com.example.prodsupport.ai.tools.security.ApplicationAccessValidator;
import com.example.prodsupport.ai.tools.security.ToolAllowlist;
import com.example.prodsupport.database.model.DatabaseHealthResult;
import com.example.prodsupport.database.service.ApplicationDatabaseDiagnosticService;
import com.example.prodsupport.domain.RegisteredApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;

@Component
public class DatabaseHealthAiTool implements Function<DatabaseDiagnosticToolRequest, ToolExecutionResult<DatabaseHealthResult>> {

    private static final Logger log = LoggerFactory.getLogger(DatabaseHealthAiTool.class);

    public static final String TOOL_NAME = ToolAllowlist.TOOL_CHECK_DATABASE_HEALTH;
    public static final String TOOL_DESCRIPTION = "Check whether the database configured for a registered application " +
            "is reachable. Use this tool when investigating database connectivity, connection failures, " +
            "database availability, or DB-related application errors. This tool is strictly read-only.";

    private final ApplicationAccessValidator accessValidator;
    private final ApplicationDatabaseDiagnosticService diagnosticService;
    private final ToolExecutionAuditor auditor;

    public DatabaseHealthAiTool(ApplicationAccessValidator accessValidator,
                                ApplicationDatabaseDiagnosticService diagnosticService,
                                ToolExecutionAuditor auditor) {
        this.accessValidator = accessValidator;
        this.diagnosticService = diagnosticService;
        this.auditor = auditor;
    }

    @Override
    public ToolExecutionResult<DatabaseHealthResult> apply(DatabaseDiagnosticToolRequest request) {
        Instant startTime = Instant.now();
        InvestigationContext context = InvestigationContextHolder.getContext();

        if (context != null) {
            context.recordToolRequested(TOOL_NAME);
            if (!context.canExecuteTool()) {
                String warning = "Investigation reached maximum diagnostic steps. Answer is based on currently available evidence.";
                context.recordWarning(warning);
                ToolExecutionResult<DatabaseHealthResult> limitResult = ToolExecutionResult.failure(TOOL_NAME, warning, startTime, 0);
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
            ToolExecutionResult<DatabaseHealthResult> invalidResult = ToolExecutionResult.failure(TOOL_NAME, warning, startTime, 0);
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
            ToolExecutionResult<DatabaseHealthResult> authResult = ToolExecutionResult.failure(TOOL_NAME, failureReason, startTime, duration);
            if (context != null) {
                context.recordToolExecution(authResult);
            }
            return authResult;
        }

        try {
            DatabaseHealthResult data = diagnosticService.checkHealth(app);
            long duration = Duration.between(startTime, Instant.now()).toMillis();
            boolean success = data.reachable();
            String failureReason = success ? null : data.message();

            auditor.audit(new ToolExecutionAudit(TOOL_NAME, app.getApplicationName(), app.getEnvironment(),
                    startTime, Instant.now(), duration, success, failureReason));

            ToolExecutionResult<DatabaseHealthResult> result = success ?
                    ToolExecutionResult.success(TOOL_NAME, data, startTime, duration) :
                    ToolExecutionResult.failure(TOOL_NAME, data.message(), startTime, duration);

            if (context != null) {
                context.recordToolExecution(result);
                if (data.reachable()) {
                    context.recordEvidence("Database connectivity is UP (type=" + data.databaseType() + ", responseTime=" + data.responseTimeMs() + "ms)");
                } else {
                    context.recordEvidence("Database is unreachable: " + data.message());
                }
            }
            return result;
        } catch (Exception ex) {
            long duration = Duration.between(startTime, Instant.now()).toMillis();
            String errorMsg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            auditor.audit(new ToolExecutionAudit(TOOL_NAME, app.getApplicationName(), app.getEnvironment(),
                    startTime, Instant.now(), duration, false, errorMsg));
            ToolExecutionResult<DatabaseHealthResult> errorResult = ToolExecutionResult.failure(TOOL_NAME, errorMsg, startTime, duration);
            if (context != null) {
                context.recordToolExecution(errorResult);
                context.recordEvidence("Database health check failed: " + errorMsg);
            }
            return errorResult;
        }
    }
}
