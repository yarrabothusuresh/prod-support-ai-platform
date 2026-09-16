package com.example.prodsupport.ai.tools;

import com.example.prodsupport.ai.tools.audit.ToolExecutionAudit;
import com.example.prodsupport.ai.tools.audit.ToolExecutionAuditor;
import com.example.prodsupport.ai.tools.context.InvestigationContext;
import com.example.prodsupport.ai.tools.context.InvestigationContextHolder;
import com.example.prodsupport.ai.tools.model.KafkaConsumerLagRequest;
import com.example.prodsupport.ai.tools.model.ToolExecutionResult;
import com.example.prodsupport.ai.tools.security.ApplicationAccessValidator;
import com.example.prodsupport.ai.tools.security.ToolAllowlist;
import com.example.prodsupport.application.service.kafka.KafkaDiagnosticService;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.infrastructure.kafka.model.KafkaConsumerLagData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;

@Component
public class KafkaConsumerLagAiTool implements Function<KafkaConsumerLagRequest, ToolExecutionResult<KafkaConsumerLagData>> {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerLagAiTool.class);

    public static final String TOOL_NAME = ToolAllowlist.TOOL_CHECK_KAFKA_CONSUMER_LAG;
    public static final String TOOL_DESCRIPTION = "Check Kafka consumer lag for a configured consumer group belonging to a registered application. " +
            "Use this tool when investigating delayed event processing, consumer backlog, slow processing, or Kafka-related application issues. " +
            "This tool is read-only and does not modify offsets.";

    private final ApplicationAccessValidator accessValidator;
    private final KafkaDiagnosticService kafkaDiagnosticService;
    private final ToolExecutionAuditor auditor;

    public KafkaConsumerLagAiTool(ApplicationAccessValidator accessValidator,
                                 KafkaDiagnosticService kafkaDiagnosticService,
                                 ToolExecutionAuditor auditor) {
        this.accessValidator = accessValidator;
        this.kafkaDiagnosticService = kafkaDiagnosticService;
        this.auditor = auditor;
    }

    @Override
    public ToolExecutionResult<KafkaConsumerLagData> apply(KafkaConsumerLagRequest request) {
        Instant startTime = Instant.now();
        InvestigationContext context = InvestigationContextHolder.getContext();

        if (context != null) {
            context.recordToolRequested(TOOL_NAME);
            if (!context.canExecuteTool()) {
                String warning = "Investigation reached maximum diagnostic steps. Answer is based on currently available evidence.";
                log.warn("Maximum tool call limit reached ({} calls). Aborting {}", context.getMaxToolCalls(), TOOL_NAME);
                context.recordWarning(warning);
                ToolExecutionResult<KafkaConsumerLagData> limitResult = ToolExecutionResult.failure(TOOL_NAME, warning, startTime, 0);
                context.recordToolExecution(limitResult);
                return limitResult;
            }
            context.incrementExecutionCount();
        }

        if (request == null || request.applicationName() == null || request.applicationName().isBlank()
                || request.environment() == null || request.environment().isBlank()
                || request.consumerGroup() == null || request.consumerGroup().isBlank()) {
            String warning = "Invalid parameters: applicationName, environment, and consumerGroup must not be blank";
            auditor.audit(new ToolExecutionAudit(TOOL_NAME, request != null ? request.applicationName() : "unknown",
                    request != null ? request.environment() : "unknown", startTime, Instant.now(), 0, false, warning));
            ToolExecutionResult<KafkaConsumerLagData> invalidResult = ToolExecutionResult.failure(TOOL_NAME, warning, startTime, 0);
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
            ToolExecutionResult<KafkaConsumerLagData> authResult = ToolExecutionResult.failure(TOOL_NAME, failureReason, startTime, duration);
            if (context != null) {
                context.recordToolExecution(authResult);
            }
            return authResult;
        }

        try {
            KafkaConsumerLagData data = kafkaDiagnosticService.checkConsumerLag(app, request.consumerGroup());
            long duration = Duration.between(startTime, Instant.now()).toMillis();
            boolean success = !"NOT_FOUND".equalsIgnoreCase(data.state()) && !"UNKNOWN".equalsIgnoreCase(data.lagStatus());
            String failureReason = success ? null : (data.warnings().isEmpty() ? "Lag calculation returned UNKNOWN" : String.join("; ", data.warnings()));

            auditor.audit(new ToolExecutionAudit(TOOL_NAME, app.getApplicationName(), app.getEnvironment(),
                    startTime, Instant.now(), duration, success, failureReason));

            ToolExecutionResult<KafkaConsumerLagData> result = ToolExecutionResult.success(TOOL_NAME, data, startTime, duration);

            if (context != null) {
                context.recordToolExecution(result);
                String lagStr = data.totalLag() != null ? String.valueOf(data.totalLag()) : "unknown";
                String highestLagStr = data.highestPartitionLag() != null ? String.valueOf(data.highestPartitionLag()) : "unknown";
                context.recordEvidence("Kafka consumer group '" + data.consumerGroup() + "' (state=" + data.state() +
                        ", members=" + data.memberCount() + ") total lag is " + lagStr +
                        " (highest partition lag=" + highestLagStr + ", classification=" + data.lagStatus() + ")");
                if (data.warnings() != null) {
                    for (String w : data.warnings()) {
                        context.recordWarning(w);
                    }
                }
            }
            return result;
        } catch (Exception ex) {
            long duration = Duration.between(startTime, Instant.now()).toMillis();
            String errorMsg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            auditor.audit(new ToolExecutionAudit(TOOL_NAME, app.getApplicationName(), app.getEnvironment(),
                    startTime, Instant.now(), duration, false, errorMsg));
            ToolExecutionResult<KafkaConsumerLagData> errorResult = ToolExecutionResult.failure(TOOL_NAME, errorMsg, startTime, duration);
            if (context != null) {
                context.recordToolExecution(errorResult);
                context.recordEvidence("Kafka consumer lag diagnostic failed for '" + request.consumerGroup() + "': " + errorMsg);
            }
            return errorResult;
        }
    }
}
