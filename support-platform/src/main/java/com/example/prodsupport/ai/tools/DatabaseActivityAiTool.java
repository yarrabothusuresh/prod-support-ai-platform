package com.example.prodsupport.ai.tools;

import com.example.prodsupport.ai.tools.audit.ToolExecutionAudit;
import com.example.prodsupport.ai.tools.audit.ToolExecutionAuditor;
import com.example.prodsupport.ai.tools.context.InvestigationContext;
import com.example.prodsupport.ai.tools.context.InvestigationContextHolder;
import com.example.prodsupport.ai.tools.model.DatabaseDiagnosticToolRequest;
import com.example.prodsupport.ai.tools.model.ToolExecutionResult;
import com.example.prodsupport.ai.tools.security.ApplicationAccessValidator;
import com.example.prodsupport.ai.tools.security.ToolAllowlist;
import com.example.prodsupport.database.model.DatabaseActivityResult;
import com.example.prodsupport.database.service.ApplicationDatabaseDiagnosticService;
import com.example.prodsupport.domain.RegisteredApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;

@Component
public class DatabaseActivityAiTool implements Function<DatabaseDiagnosticToolRequest, ToolExecutionResult<DatabaseActivityResult>> {

    private static final Logger log = LoggerFactory.getLogger(DatabaseActivityAiTool.class);

    public static final String TOOL_NAME = ToolAllowlist.TOOL_CHECK_DATABASE_ACTIVITY;
    public static final String TOOL_DESCRIPTION = "Retrieve safe aggregate database activity information for the " +
            "registered application's configured database. Use this when investigating database latency, " +
            "waiting sessions, long-running activity, or database pressure. Returns aggregate diagnostic metadata only.";

    private final ApplicationAccessValidator accessValidator;
    private final ApplicationDatabaseDiagnosticService diagnosticService;
    private final ToolExecutionAuditor auditor;

    public DatabaseActivityAiTool(ApplicationAccessValidator accessValidator,
                                  ApplicationDatabaseDiagnosticService diagnosticService,
                                  ToolExecutionAuditor auditor) {
        this.accessValidator = accessValidator;
        this.diagnosticService = diagnosticService;
        this.auditor = auditor;
    }

    @Override
    public ToolExecutionResult<DatabaseActivityResult> apply(DatabaseDiagnosticToolRequest request) {
        Instant startTime = Instant.now();
        InvestigationContext context = InvestigationContextHolder.getContext();

        if (context != null) {
            context.recordToolRequested(TOOL_NAME);
            if (!context.canExecuteTool()) {
                String warning = "Investigation reached maximum diagnostic steps. Answer is based on currently available evidence.";
                context.recordWarning(warning);
                ToolExecutionResult<DatabaseActivityResult> limitResult = ToolExecutionResult.failure(TOOL_NAME, warning, startTime, 0);
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
            ToolExecutionResult<DatabaseActivityResult> invalidResult = ToolExecutionResult.failure(TOOL_NAME, warning, startTime, 0);
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
            ToolExecutionResult<DatabaseActivityResult> authResult = ToolExecutionResult.failure(TOOL_NAME, failureReason, startTime, duration);
            if (context != null) {
                context.recordToolExecution(authResult);
            }
            return authResult;
        }

        try {
            DatabaseActivityResult data = diagnosticService.checkActivity(app);
            long duration = Duration.between(startTime, Instant.now()).toMillis();
            boolean success = data.warnings() == null || data.warnings().isEmpty() || data.activeSessions() > 0;
            String failureReason = success ? null : (data.warnings() != null && !data.warnings().isEmpty() ? data.warnings().get(0) : "Activity unavailable");

            auditor.audit(new ToolExecutionAudit(TOOL_NAME, app.getApplicationName(), app.getEnvironment(),
                    startTime, Instant.now(), duration, success, failureReason));

            ToolExecutionResult<DatabaseActivityResult> result = success ?
                    ToolExecutionResult.success(TOOL_NAME, data, startTime, duration) :
                    ToolExecutionResult.failure(TOOL_NAME, failureReason, startTime, duration);

            if (context != null) {
                context.recordToolExecution(result);
                if (success) {
                    context.recordEvidence(String.format("Database activity: activeSessions=%d, idleSessions=%d, idleInTransaction=%d, waitingSessions=%d, longRunningQueries=%d",
                            data.activeSessions(), data.idleSessions(), data.idleInTransactionSessions(),
                            data.waitingSessions(), data.longRunningQueryCount()));
                } else {
                    context.recordEvidence("Database activity metadata unavailable: " + failureReason);
                }
            }
            return result;
        } catch (Exception ex) {
            long duration = Duration.between(startTime, Instant.now()).toMillis();
            String errorMsg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            auditor.audit(new ToolExecutionAudit(TOOL_NAME, app.getApplicationName(), app.getEnvironment(),
                    startTime, Instant.now(), duration, false, errorMsg));
            ToolExecutionResult<DatabaseActivityResult> errorResult = ToolExecutionResult.failure(TOOL_NAME, errorMsg, startTime, duration);
            if (context != null) {
                context.recordToolExecution(errorResult);
                context.recordEvidence("Database activity check failed: " + errorMsg);
            }
            return errorResult;
        }
    }
}
