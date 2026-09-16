package com.example.prodsupport.ai.tools;

import com.example.prodsupport.ai.tools.audit.ToolExecutionAudit;
import com.example.prodsupport.ai.tools.audit.ToolExecutionAuditor;
import com.example.prodsupport.ai.tools.context.InvestigationContext;
import com.example.prodsupport.ai.tools.context.InvestigationContextHolder;
import com.example.prodsupport.ai.tools.model.KafkaClusterRequest;
import com.example.prodsupport.ai.tools.model.ToolExecutionResult;
import com.example.prodsupport.ai.tools.security.ApplicationAccessValidator;
import com.example.prodsupport.ai.tools.security.ToolAllowlist;
import com.example.prodsupport.application.service.kafka.KafkaDiagnosticService;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.infrastructure.kafka.model.KafkaClusterDiagnosticData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;

@Component
public class KafkaClusterAiTool implements Function<KafkaClusterRequest, ToolExecutionResult<KafkaClusterDiagnosticData>> {

    private static final Logger log = LoggerFactory.getLogger(KafkaClusterAiTool.class);

    public static final String TOOL_NAME = ToolAllowlist.TOOL_CHECK_KAFKA_CLUSTER;
    public static final String TOOL_DESCRIPTION = "Check whether the Kafka cluster configured for a registered application " +
            "is reachable and return basic cluster health information. Use this tool when investigating Kafka connectivity or availability.";

    private final ApplicationAccessValidator accessValidator;
    private final KafkaDiagnosticService kafkaDiagnosticService;
    private final ToolExecutionAuditor auditor;

    public KafkaClusterAiTool(ApplicationAccessValidator accessValidator,
                              KafkaDiagnosticService kafkaDiagnosticService,
                              ToolExecutionAuditor auditor) {
        this.accessValidator = accessValidator;
        this.kafkaDiagnosticService = kafkaDiagnosticService;
        this.auditor = auditor;
    }

    @Override
    public ToolExecutionResult<KafkaClusterDiagnosticData> apply(KafkaClusterRequest request) {
        Instant startTime = Instant.now();
        InvestigationContext context = InvestigationContextHolder.getContext();

        if (context != null) {
            context.recordToolRequested(TOOL_NAME);
            if (!context.canExecuteTool()) {
                String warning = "Investigation reached maximum diagnostic steps. Answer is based on currently available evidence.";
                log.warn("Maximum tool call limit reached ({} calls). Aborting {}", context.getMaxToolCalls(), TOOL_NAME);
                context.recordWarning(warning);
                ToolExecutionResult<KafkaClusterDiagnosticData> limitResult = ToolExecutionResult.failure(TOOL_NAME, warning, startTime, 0);
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
            ToolExecutionResult<KafkaClusterDiagnosticData> invalidResult = ToolExecutionResult.failure(TOOL_NAME, warning, startTime, 0);
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
            ToolExecutionResult<KafkaClusterDiagnosticData> authResult = ToolExecutionResult.failure(TOOL_NAME, failureReason, startTime, duration);
            if (context != null) {
                context.recordToolExecution(authResult);
            }
            return authResult;
        }

        try {
            KafkaClusterDiagnosticData data = kafkaDiagnosticService.checkCluster(app);
            long duration = Duration.between(startTime, Instant.now()).toMillis();
            boolean success = data.reachable();
            String failureReason = success ? null : data.warning();

            auditor.audit(new ToolExecutionAudit(TOOL_NAME, app.getApplicationName(), app.getEnvironment(),
                    startTime, Instant.now(), duration, success, failureReason));

            ToolExecutionResult<KafkaClusterDiagnosticData> result = success ?
                    ToolExecutionResult.success(TOOL_NAME, data, startTime, duration) :
                    ToolExecutionResult.failure(TOOL_NAME, data.warning(), startTime, duration);

            if (context != null) {
                context.recordToolExecution(result);
                if (data.reachable()) {
                    context.recordEvidence("Kafka cluster is reachable (clusterId=" + data.clusterId() + ", brokerCount=" + data.brokerCount() + ")");
                } else {
                    context.recordEvidence("Kafka cluster is unreachable: " + data.warning());
                }
            }
            return result;
        } catch (Exception ex) {
            long duration = Duration.between(startTime, Instant.now()).toMillis();
            String errorMsg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            auditor.audit(new ToolExecutionAudit(TOOL_NAME, app.getApplicationName(), app.getEnvironment(),
                    startTime, Instant.now(), duration, false, errorMsg));
            ToolExecutionResult<KafkaClusterDiagnosticData> errorResult = ToolExecutionResult.failure(TOOL_NAME, errorMsg, startTime, duration);
            if (context != null) {
                context.recordToolExecution(errorResult);
                context.recordEvidence("Kafka cluster diagnostic failed: " + errorMsg);
            }
            return errorResult;
        }
    }
}
