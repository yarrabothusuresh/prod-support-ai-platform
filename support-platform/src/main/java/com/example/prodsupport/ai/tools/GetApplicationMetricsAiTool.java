package com.example.prodsupport.ai.tools;

import com.example.prodsupport.ai.tools.audit.ToolExecutionAudit;
import com.example.prodsupport.ai.tools.audit.ToolExecutionAuditor;
import com.example.prodsupport.ai.tools.context.InvestigationContext;
import com.example.prodsupport.ai.tools.context.InvestigationContextHolder;
import com.example.prodsupport.ai.tools.model.GetApplicationMetricsRequest;
import com.example.prodsupport.ai.tools.model.ToolExecutionResult;
import com.example.prodsupport.ai.tools.security.ApplicationAccessValidator;
import com.example.prodsupport.ai.tools.security.ToolAllowlist;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.metrics.dto.ApplicationMetricsSummaryDto;
import com.example.prodsupport.metrics.evidence.MetricEvidenceMapper;
import com.example.prodsupport.metrics.service.ApplicationMetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;

@Component
public class GetApplicationMetricsAiTool implements Function<GetApplicationMetricsRequest, ToolExecutionResult<ApplicationMetricsSummaryDto>> {

    private static final Logger log = LoggerFactory.getLogger(GetApplicationMetricsAiTool.class);

    public static final String TOOL_NAME = ToolAllowlist.TOOL_GET_APPLICATION_METRICS;
    public static final String TOOL_DESCRIPTION = "Retrieve a safe summary of current and recent metrics " +
            "(availability, HTTP traffic/errors/latency, JVM heap, CPU, database pool) for a registered application. " +
            "Use this tool when investigating performance, traffic, errors, CPU, memory or saturation. " +
            "The tool uses predefined Prometheus queries and does not accept arbitrary PromQL.";

    private final ApplicationAccessValidator accessValidator;
    private final ApplicationMetricsService metricsService;
    private final MetricEvidenceMapper evidenceMapper;
    private final ToolExecutionAuditor auditor;

    public GetApplicationMetricsAiTool(ApplicationAccessValidator accessValidator,
                                      ApplicationMetricsService metricsService,
                                      MetricEvidenceMapper evidenceMapper,
                                      ToolExecutionAuditor auditor) {
        this.accessValidator = accessValidator;
        this.metricsService = metricsService;
        this.evidenceMapper = evidenceMapper;
        this.auditor = auditor;
    }

    @Override
    public ToolExecutionResult<ApplicationMetricsSummaryDto> apply(GetApplicationMetricsRequest request) {
        Instant startTime = Instant.now();
        InvestigationContext context = InvestigationContextHolder.getContext();

        if (context != null) {
            context.recordToolRequested(TOOL_NAME);
            if (!context.canExecuteTool()) {
                String warning = "Investigation reached maximum diagnostic steps. Answer is based on currently available evidence.";
                context.recordWarning(warning);
                ToolExecutionResult<ApplicationMetricsSummaryDto> limitResult = ToolExecutionResult.failure(TOOL_NAME, warning, startTime, 0);
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
            ToolExecutionResult<ApplicationMetricsSummaryDto> invalidResult = ToolExecutionResult.failure(TOOL_NAME, warning, startTime, 0);
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
            ToolExecutionResult<ApplicationMetricsSummaryDto> authResult = ToolExecutionResult.failure(TOOL_NAME, failureReason, startTime, duration);
            if (context != null) {
                context.recordToolExecution(authResult);
            }
            return authResult;
        }

        int minutes = (request.minutes() != null && request.minutes() > 0) ? request.minutes() : 15;

        try {
            ApplicationMetricsSummaryDto summary = metricsService.getMetricsSummary(app, minutes);
            long duration = Duration.between(startTime, Instant.now()).toMillis();
            boolean success = summary != null && (summary.warnings() == null || summary.warnings().isEmpty());

            auditor.audit(new ToolExecutionAudit(TOOL_NAME, app.getApplicationName(), app.getEnvironment(),
                    startTime, Instant.now(), duration, true, null));

            ToolExecutionResult<ApplicationMetricsSummaryDto> result = ToolExecutionResult.success(TOOL_NAME, summary, startTime, duration);

            if (context != null) {
                context.recordToolExecution(result);
                String evidenceText = evidenceMapper.buildMetricsSummaryEvidence(summary);
                context.recordEvidence(evidenceText);
            }

            return result;
        } catch (Exception ex) {
            long duration = Duration.between(startTime, Instant.now()).toMillis();
            String errorMsg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            auditor.audit(new ToolExecutionAudit(TOOL_NAME, app.getApplicationName(), app.getEnvironment(),
                    startTime, Instant.now(), duration, false, errorMsg));
            ToolExecutionResult<ApplicationMetricsSummaryDto> errorResult = ToolExecutionResult.failure(TOOL_NAME, errorMsg, startTime, duration);
            if (context != null) {
                context.recordToolExecution(errorResult);
                context.recordEvidence("Application metrics collection failed: " + errorMsg);
            }
            return errorResult;
        }
    }
}
