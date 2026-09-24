package com.example.prodsupport.ai.tools;

import com.example.prodsupport.ai.tools.audit.ToolExecutionAudit;
import com.example.prodsupport.ai.tools.audit.ToolExecutionAuditor;
import com.example.prodsupport.ai.tools.context.InvestigationContext;
import com.example.prodsupport.ai.tools.context.InvestigationContextHolder;
import com.example.prodsupport.ai.tools.model.GetResourceMetricsRequest;
import com.example.prodsupport.ai.tools.model.ToolExecutionResult;
import com.example.prodsupport.ai.tools.security.ApplicationAccessValidator;
import com.example.prodsupport.ai.tools.security.ToolAllowlist;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.metrics.dto.ApplicationMetricsSummaryDto.CpuMetricsDto;
import com.example.prodsupport.metrics.entity.ApplicationMetricsConfigEntity;
import com.example.prodsupport.metrics.service.ApplicationMetricsConfigService;
import com.example.prodsupport.metrics.service.ApplicationMetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Component
public class GetResourceMetricsAiTool implements Function<GetResourceMetricsRequest, ToolExecutionResult<CpuMetricsDto>> {

    private static final Logger log = LoggerFactory.getLogger(GetResourceMetricsAiTool.class);

    public static final String TOOL_NAME = ToolAllowlist.TOOL_GET_RESOURCE_METRICS;
    public static final String TOOL_DESCRIPTION = "Retrieve safe host and process CPU metrics " +
            "for a registered application. Use this tool when investigating CPU saturation or system workload.";

    private final ApplicationAccessValidator accessValidator;
    private final ApplicationMetricsConfigService configService;
    private final ApplicationMetricsService metricsService;
    private final ToolExecutionAuditor auditor;

    public GetResourceMetricsAiTool(ApplicationAccessValidator accessValidator,
                                   ApplicationMetricsConfigService configService,
                                   ApplicationMetricsService metricsService,
                                   ToolExecutionAuditor auditor) {
        this.accessValidator = accessValidator;
        this.configService = configService;
        this.metricsService = metricsService;
        this.auditor = auditor;
    }

    @Override
    public ToolExecutionResult<CpuMetricsDto> apply(GetResourceMetricsRequest request) {
        Instant startTime = Instant.now();
        InvestigationContext context = InvestigationContextHolder.getContext();

        if (context != null) {
            context.recordToolRequested(TOOL_NAME);
            if (!context.canExecuteTool()) {
                String warning = "Investigation reached maximum diagnostic steps. Answer is based on currently available evidence.";
                context.recordWarning(warning);
                ToolExecutionResult<CpuMetricsDto> limitResult = ToolExecutionResult.failure(TOOL_NAME, warning, startTime, 0);
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
            ToolExecutionResult<CpuMetricsDto> invalidResult = ToolExecutionResult.failure(TOOL_NAME, warning, startTime, 0);
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
            ToolExecutionResult<CpuMetricsDto> authResult = ToolExecutionResult.failure(TOOL_NAME, failureReason, startTime, duration);
            if (context != null) {
                context.recordToolExecution(authResult);
            }
            return authResult;
        }

        ApplicationMetricsConfigEntity config = configService.findEntityByApplication(app)
                .orElseGet(() -> new ApplicationMetricsConfigEntity(app, true, app.getApplicationName(), app.getApplicationName()));

        try {
            List<String> warnings = new ArrayList<>();
            CpuMetricsDto cpu = metricsService.getCpuMetrics(config.getApplicationLabel(), warnings);
            long duration = Duration.between(startTime, Instant.now()).toMillis();

            auditor.audit(new ToolExecutionAudit(TOOL_NAME, app.getApplicationName(), app.getEnvironment(),
                    startTime, Instant.now(), duration, true, null));

            ToolExecutionResult<CpuMetricsDto> result = ToolExecutionResult.success(TOOL_NAME, cpu, startTime, duration);

            if (context != null) {
                context.recordToolExecution(result);
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("Resource CPU metrics for %s: ", app.getApplicationName()));
                sb.append(String.format("processCpu=%.1f%%, systemCpu=%.1f%%",
                        cpu.processCpuPercent() != null ? cpu.processCpuPercent() : 0.0,
                        cpu.systemCpuPercent() != null ? cpu.systemCpuPercent() : 0.0));
                context.recordEvidence(sb.toString());
            }

            return result;
        } catch (Exception ex) {
            long duration = Duration.between(startTime, Instant.now()).toMillis();
            String errorMsg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            auditor.audit(new ToolExecutionAudit(TOOL_NAME, app.getApplicationName(), app.getEnvironment(),
                    startTime, Instant.now(), duration, false, errorMsg));
            ToolExecutionResult<CpuMetricsDto> errorResult = ToolExecutionResult.failure(TOOL_NAME, errorMsg, startTime, duration);
            if (context != null) {
                context.recordToolExecution(errorResult);
                context.recordEvidence("Resource CPU metrics check failed: " + errorMsg);
            }
            return errorResult;
        }
    }
}
