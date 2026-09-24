package com.example.prodsupport.ai.tools;

import com.example.prodsupport.ai.tools.audit.ToolExecutionAudit;
import com.example.prodsupport.ai.tools.audit.ToolExecutionAuditor;
import com.example.prodsupport.ai.tools.context.InvestigationContext;
import com.example.prodsupport.ai.tools.context.InvestigationContextHolder;
import com.example.prodsupport.ai.tools.model.GetActiveAlertsRequest;
import com.example.prodsupport.ai.tools.model.ToolExecutionResult;
import com.example.prodsupport.ai.tools.security.ApplicationAccessValidator;
import com.example.prodsupport.ai.tools.security.ToolAllowlist;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.metrics.evidence.MetricEvidenceMapper;
import com.example.prodsupport.metrics.service.PrometheusAlertService;
import com.example.prodsupport.metrics.service.PrometheusAlertService.ActiveAlertsResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;

@Component
public class GetActiveAlertsAiTool implements Function<GetActiveAlertsRequest, ToolExecutionResult<ActiveAlertsResult>> {

    private static final Logger log = LoggerFactory.getLogger(GetActiveAlertsAiTool.class);

    public static final String TOOL_NAME = ToolAllowlist.TOOL_GET_ACTIVE_ALERTS;
    public static final String TOOL_DESCRIPTION = "Retrieve active Prometheus alerts associated with " +
            "a registered application and environment. Use this tool when investigating whether " +
            "monitoring currently reports an active alert condition (e.g. HighHttpErrorRate, HighHttpLatency, HighDatabasePoolUtilization, ApplicationDown).";

    private final ApplicationAccessValidator accessValidator;
    private final PrometheusAlertService alertService;
    private final MetricEvidenceMapper evidenceMapper;
    private final ToolExecutionAuditor auditor;

    public GetActiveAlertsAiTool(ApplicationAccessValidator accessValidator,
                                PrometheusAlertService alertService,
                                MetricEvidenceMapper evidenceMapper,
                                ToolExecutionAuditor auditor) {
        this.accessValidator = accessValidator;
        this.alertService = alertService;
        this.evidenceMapper = evidenceMapper;
        this.auditor = auditor;
    }

    @Override
    public ToolExecutionResult<ActiveAlertsResult> apply(GetActiveAlertsRequest request) {
        Instant startTime = Instant.now();
        InvestigationContext context = InvestigationContextHolder.getContext();

        if (context != null) {
            context.recordToolRequested(TOOL_NAME);
            if (!context.canExecuteTool()) {
                String warning = "Investigation reached maximum diagnostic steps. Answer is based on currently available evidence.";
                context.recordWarning(warning);
                ToolExecutionResult<ActiveAlertsResult> limitResult = ToolExecutionResult.failure(TOOL_NAME, warning, startTime, 0);
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
            ToolExecutionResult<ActiveAlertsResult> invalidResult = ToolExecutionResult.failure(TOOL_NAME, warning, startTime, 0);
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
            ToolExecutionResult<ActiveAlertsResult> authResult = ToolExecutionResult.failure(TOOL_NAME, failureReason, startTime, duration);
            if (context != null) {
                context.recordToolExecution(authResult);
            }
            return authResult;
        }

        try {
            ActiveAlertsResult result = alertService.getActiveAlerts(app);
            long duration = Duration.between(startTime, Instant.now()).toMillis();

            auditor.audit(new ToolExecutionAudit(TOOL_NAME, app.getApplicationName(), app.getEnvironment(),
                    startTime, Instant.now(), duration, true, null));

            ToolExecutionResult<ActiveAlertsResult> toolResult = ToolExecutionResult.success(TOOL_NAME, result, startTime, duration);

            if (context != null) {
                context.recordToolExecution(toolResult);
                String evidenceText = evidenceMapper.buildAlertsEvidence(
                        app.getApplicationName(),
                        app.getEnvironment(),
                        result.activeAlerts(),
                        result.warnings()
                );
                context.recordEvidence(evidenceText);
            }

            return toolResult;
        } catch (Exception ex) {
            long duration = Duration.between(startTime, Instant.now()).toMillis();
            String errorMsg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            auditor.audit(new ToolExecutionAudit(TOOL_NAME, app.getApplicationName(), app.getEnvironment(),
                    startTime, Instant.now(), duration, false, errorMsg));
            ToolExecutionResult<ActiveAlertsResult> errorResult = ToolExecutionResult.failure(TOOL_NAME, errorMsg, startTime, duration);
            if (context != null) {
                context.recordToolExecution(errorResult);
                context.recordEvidence("Active alerts check failed: " + errorMsg);
            }
            return errorResult;
        }
    }
}
