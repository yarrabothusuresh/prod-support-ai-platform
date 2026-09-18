package com.example.prodsupport.ai.tools;

import com.example.prodsupport.ai.tools.audit.ToolExecutionAudit;
import com.example.prodsupport.ai.tools.audit.ToolExecutionAuditor;
import com.example.prodsupport.ai.tools.context.InvestigationContext;
import com.example.prodsupport.ai.tools.context.InvestigationContextHolder;
import com.example.prodsupport.ai.tools.model.ApplicationLogTimelineRequest;
import com.example.prodsupport.ai.tools.model.ToolExecutionResult;
import com.example.prodsupport.ai.tools.security.ApplicationAccessValidator;
import com.example.prodsupport.ai.tools.security.ToolAllowlist;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.logging.model.LogTimelineEventDto;
import com.example.prodsupport.logging.model.LogTimelineResult;
import com.example.prodsupport.logging.service.LogTimelineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;

@Component
public class GetApplicationLogTimelineAiTool implements Function<ApplicationLogTimelineRequest, ToolExecutionResult<LogTimelineResult>> {

    private static final Logger log = LoggerFactory.getLogger(GetApplicationLogTimelineAiTool.class);

    public static final String TOOL_NAME = ToolAllowlist.TOOL_GET_APPLICATION_LOG_TIMELINE;
    public static final String TOOL_DESCRIPTION = "Retrieve a bounded chronological timeline of relevant application log events for a registered application. " +
            "Use this when investigating the sequence of events around an incident or a specific correlation ID. " +
            "Chronological order alone does not prove causality.";

    private final ApplicationAccessValidator accessValidator;
    private final LogTimelineService logTimelineService;
    private final ToolExecutionAuditor auditor;

    public GetApplicationLogTimelineAiTool(ApplicationAccessValidator accessValidator,
                                          LogTimelineService logTimelineService,
                                          ToolExecutionAuditor auditor) {
        this.accessValidator = accessValidator;
        this.logTimelineService = logTimelineService;
        this.auditor = auditor;
    }

    @Override
    public ToolExecutionResult<LogTimelineResult> apply(ApplicationLogTimelineRequest request) {
        Instant startTime = Instant.now();
        InvestigationContext context = InvestigationContextHolder.getContext();

        if (context != null) {
            context.recordToolRequested(TOOL_NAME);
            if (!context.canExecuteTool()) {
                String warning = "Investigation reached maximum diagnostic steps. Answer is based on currently available evidence.";
                log.warn("Maximum tool call limit reached ({} calls). Aborting {}", context.getMaxToolCalls(), TOOL_NAME);
                context.recordWarning(warning);
                ToolExecutionResult<LogTimelineResult> limitResult = ToolExecutionResult.failure(TOOL_NAME, warning, startTime, 0);
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
            ToolExecutionResult<LogTimelineResult> invalidResult = ToolExecutionResult.failure(TOOL_NAME, warning, startTime, 0);
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
            ToolExecutionResult<LogTimelineResult> authResult = ToolExecutionResult.failure(TOOL_NAME, failureReason, startTime, duration);
            if (context != null) {
                context.recordToolExecution(authResult);
            }
            return authResult;
        }

        int minutes = request.minutes() != null && request.minutes() > 0 ? request.minutes() : 15;
        Instant end = Instant.now();
        Instant start = end.minus(Duration.ofMinutes(minutes));

        try {
            LogTimelineResult result = logTimelineService.getTimeline(
                    app.getApplicationName(),
                    app.getEnvironment(),
                    start,
                    end,
                    request.correlationId(),
                    30
            );

            long duration = Duration.between(startTime, Instant.now()).toMillis();
            auditor.audit(new ToolExecutionAudit(TOOL_NAME, app.getApplicationName(), app.getEnvironment(),
                    startTime, Instant.now(), duration, true, null));
            ToolExecutionResult<LogTimelineResult> successResult = ToolExecutionResult.success(TOOL_NAME, result, startTime, duration);

            if (context != null) {
                context.recordToolExecution(successResult);
                if (result.events().isEmpty()) {
                    context.recordEvidence("Log timeline: No chronological events recorded over last " + minutes + "m"
                            + (request.correlationId() != null ? " for corrId=" + request.correlationId() : ""));
                } else {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Log timeline (").append(result.events().size()).append(" events): ");
                    int count = Math.min(4, result.events().size());
                    for (int i = 0; i < count; i++) {
                        LogTimelineEventDto ev = result.events().get(i);
                        if (i > 0) sb.append(" -> ");
                        sb.append("[").append(ev.timestamp() != null ? ev.timestamp() : "T").append(" ")
                                .append(ev.level()).append(" ").append(ev.message()).append("]");
                    }
                    context.recordEvidence(sb.toString());
                }
            }

            return successResult;

        } catch (Exception ex) {
            long duration = Duration.between(startTime, Instant.now()).toMillis();
            String errorMsg = ex.getMessage();
            String warning = "Log timeline currently unavailable (" + errorMsg + ")";
            auditor.audit(new ToolExecutionAudit(TOOL_NAME, app.getApplicationName(), app.getEnvironment(),
                    startTime, Instant.now(), duration, false, errorMsg));
            ToolExecutionResult<LogTimelineResult> failResult = ToolExecutionResult.failure(TOOL_NAME, warning, startTime, duration);
            if (context != null) {
                context.recordToolExecution(failResult);
                context.recordWarning(warning);
            }
            return failResult;
        }
    }
}
