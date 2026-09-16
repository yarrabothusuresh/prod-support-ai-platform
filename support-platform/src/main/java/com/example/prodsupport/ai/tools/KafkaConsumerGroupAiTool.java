package com.example.prodsupport.ai.tools;

import com.example.prodsupport.ai.tools.audit.ToolExecutionAudit;
import com.example.prodsupport.ai.tools.audit.ToolExecutionAuditor;
import com.example.prodsupport.ai.tools.context.InvestigationContext;
import com.example.prodsupport.ai.tools.context.InvestigationContextHolder;
import com.example.prodsupport.ai.tools.model.KafkaConsumerGroupRequest;
import com.example.prodsupport.ai.tools.model.ToolExecutionResult;
import com.example.prodsupport.ai.tools.security.ApplicationAccessValidator;
import com.example.prodsupport.ai.tools.security.ToolAllowlist;
import com.example.prodsupport.application.service.kafka.KafkaDiagnosticService;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.infrastructure.kafka.model.KafkaConsumerGroupData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;

@Component
public class KafkaConsumerGroupAiTool implements Function<KafkaConsumerGroupRequest, ToolExecutionResult<KafkaConsumerGroupData>> {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerGroupAiTool.class);

    public static final String TOOL_NAME = ToolAllowlist.TOOL_CHECK_KAFKA_CONSUMER_GROUP;
    public static final String TOOL_DESCRIPTION = "Inspect the configured Kafka consumer group for an onboarded application " +
            "and return consumer group state, coordinator, and active member count. Use this when investigating consumer availability, " +
            "rebalancing, or whether a consumer group is active.";

    private final ApplicationAccessValidator accessValidator;
    private final KafkaDiagnosticService kafkaDiagnosticService;
    private final ToolExecutionAuditor auditor;

    public KafkaConsumerGroupAiTool(ApplicationAccessValidator accessValidator,
                                    KafkaDiagnosticService kafkaDiagnosticService,
                                    ToolExecutionAuditor auditor) {
        this.accessValidator = accessValidator;
        this.kafkaDiagnosticService = kafkaDiagnosticService;
        this.auditor = auditor;
    }

    @Override
    public ToolExecutionResult<KafkaConsumerGroupData> apply(KafkaConsumerGroupRequest request) {
        Instant startTime = Instant.now();
        InvestigationContext context = InvestigationContextHolder.getContext();

        if (context != null) {
            context.recordToolRequested(TOOL_NAME);
            if (!context.canExecuteTool()) {
                String warning = "Investigation reached maximum diagnostic steps. Answer is based on currently available evidence.";
                log.warn("Maximum tool call limit reached ({} calls). Aborting {}", context.getMaxToolCalls(), TOOL_NAME);
                context.recordWarning(warning);
                ToolExecutionResult<KafkaConsumerGroupData> limitResult = ToolExecutionResult.failure(TOOL_NAME, warning, startTime, 0);
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
            ToolExecutionResult<KafkaConsumerGroupData> invalidResult = ToolExecutionResult.failure(TOOL_NAME, warning, startTime, 0);
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
            ToolExecutionResult<KafkaConsumerGroupData> authResult = ToolExecutionResult.failure(TOOL_NAME, failureReason, startTime, duration);
            if (context != null) {
                context.recordToolExecution(authResult);
            }
            return authResult;
        }

        try {
            KafkaConsumerGroupData data = kafkaDiagnosticService.checkConsumerGroup(app, request.consumerGroup());
            long duration = Duration.between(startTime, Instant.now()).toMillis();
            boolean success = !"NOT_FOUND".equalsIgnoreCase(data.state()) && data.warning() == null;
            String failureReason = success ? null : data.warning();

            auditor.audit(new ToolExecutionAudit(TOOL_NAME, app.getApplicationName(), app.getEnvironment(),
                    startTime, Instant.now(), duration, success, failureReason));

            ToolExecutionResult<KafkaConsumerGroupData> result = success ?
                    ToolExecutionResult.success(TOOL_NAME, data, startTime, duration) :
                    ToolExecutionResult.failure(TOOL_NAME, data.warning() != null ? data.warning() : "Consumer group inspection failed", startTime, duration);

            if (context != null) {
                context.recordToolExecution(result);
                context.recordEvidence("Kafka consumer group '" + data.consumerGroup() + "' state is " + data.state() +
                        " with " + data.memberCount() + " active member(s)");
            }
            return result;
        } catch (Exception ex) {
            long duration = Duration.between(startTime, Instant.now()).toMillis();
            String errorMsg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            auditor.audit(new ToolExecutionAudit(TOOL_NAME, app.getApplicationName(), app.getEnvironment(),
                    startTime, Instant.now(), duration, false, errorMsg));
            ToolExecutionResult<KafkaConsumerGroupData> errorResult = ToolExecutionResult.failure(TOOL_NAME, errorMsg, startTime, duration);
            if (context != null) {
                context.recordToolExecution(errorResult);
                context.recordEvidence("Kafka consumer group diagnostic failed for '" + request.consumerGroup() + "': " + errorMsg);
            }
            return errorResult;
        }
    }
}
