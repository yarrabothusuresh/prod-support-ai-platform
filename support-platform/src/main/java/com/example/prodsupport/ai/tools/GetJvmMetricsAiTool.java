package com.example.prodsupport.ai.tools;

import com.example.prodsupport.ai.tools.audit.ToolExecutionAudit;
import com.example.prodsupport.ai.tools.audit.ToolExecutionAuditor;
import com.example.prodsupport.ai.tools.context.InvestigationContext;
import com.example.prodsupport.ai.tools.context.InvestigationContextHolder;
import com.example.prodsupport.ai.tools.model.GetJvmMetricsRequest;
import com.example.prodsupport.ai.tools.model.ToolExecutionResult;
import com.example.prodsupport.ai.tools.security.ApplicationAccessValidator;
import com.example.prodsupport.ai.tools.security.ToolAllowlist;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.metrics.dto.ApplicationMetricsSummaryDto.JvmMetricsDto;
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
public class GetJvmMetricsAiTool implements Function<GetJvmMetricsRequest, ToolExecutionResult<JvmMetricsDto>> {

    private static final Logger log = LoggerFactory.getLogger(GetJvmMetricsAiTool.class);

    public static final String TOOL_NAME = ToolAllowlist.TOOL_GET_JVM_METRICS;
    public static final String TOOL_DESCRIPTION = "Retrieve safe JVM memory, GC and runtime metrics " +
            "for a registered Java application. Use this tool when investigating JVM pressure, " +
            "memory usage or runtime resource problems.";

    private final ApplicationAccessValidator accessValidator;
    private final ApplicationMetricsConfigService configService;
    private final ApplicationMetricsService metricsService;
    private final ToolExecutionAuditor auditor;

    public GetJvmMetricsAiTool(ApplicationAccessValidator accessValidator,
                              ApplicationMetricsConfigService configService,
                              ApplicationMetricsService metricsService,
                              ToolExecutionAuditor auditor) {
        this.accessValidator = accessValidator;
        this.configService = configService;
        this.metricsService = metricsService;
        this.auditor = auditor;
    }

    @Override
    public ToolExecutionResult<JvmMetricsDto> apply(GetJvmMetricsRequest request) {
        Instant startTime = Instant.now();
        InvestigationContext context = InvestigationContextHolder.getContext();

        if (context != null) {
            context.recordToolRequested(TOOL_NAME);
            if (!context.canExecuteTool()) {
                String warning = "Investigation reached maximum diagnostic steps. Answer is based on currently available evidence.";
                context.recordWarning(warning);
                ToolExecutionResult<JvmMetricsDto> limitResult = ToolExecutionResult.failure(TOOL_NAME, warning, startTime, 0);
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
            ToolExecutionResult<JvmMetricsDto> invalidResult = ToolExecutionResult.failure(TOOL_NAME, warning, startTime, 0);
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
            ToolExecutionResult<JvmMetricsDto> authResult = ToolExecutionResult.failure(TOOL_NAME, failureReason, startTime, duration);
            if (context != null) {
                context.recordToolExecution(authResult);
            }
            return authResult;
        }

        ApplicationMetricsConfigEntity config = configService.findEntityByApplication(app)
                .orElseGet(() -> new ApplicationMetricsConfigEntity(app, true, app.getApplicationName(), app.getApplicationName()));

        try {
            List<String> warnings = new ArrayList<>();
            JvmMetricsDto jvm = metricsService.getJvmMetrics(config.getApplicationLabel(), warnings);
            long duration = Duration.between(startTime, Instant.now()).toMillis();

            auditor.audit(new ToolExecutionAudit(TOOL_NAME, app.getApplicationName(), app.getEnvironment(),
                    startTime, Instant.now(), duration, true, null));

            ToolExecutionResult<JvmMetricsDto> result = ToolExecutionResult.success(TOOL_NAME, jvm, startTime, duration);

            if (context != null) {
                context.recordToolExecution(result);
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("JVM metrics for %s: ", app.getApplicationName()));
                sb.append(String.format("heapUsed=%.1f MB, heapMax=%.1f MB, heapUtil=%.1f%%, nonHeapUsed=%.1f MB",
                        jvm.heapUsedMb() != null ? jvm.heapUsedMb() : 0.0,
                        jvm.heapMaxMb() != null ? jvm.heapMaxMb() : 0.0,
                        jvm.heapUtilizationPercent() != null ? jvm.heapUtilizationPercent() : 0.0,
                        jvm.nonHeapUsedMb() != null ? jvm.nonHeapUsedMb() : 0.0));
                context.recordEvidence(sb.toString());
            }

            return result;
        } catch (Exception ex) {
            long duration = Duration.between(startTime, Instant.now()).toMillis();
            String errorMsg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            auditor.audit(new ToolExecutionAudit(TOOL_NAME, app.getApplicationName(), app.getEnvironment(),
                    startTime, Instant.now(), duration, false, errorMsg));
            ToolExecutionResult<JvmMetricsDto> errorResult = ToolExecutionResult.failure(TOOL_NAME, errorMsg, startTime, duration);
            if (context != null) {
                context.recordToolExecution(errorResult);
                context.recordEvidence("JVM metrics check failed: " + errorMsg);
            }
            return errorResult;
        }
    }
}
