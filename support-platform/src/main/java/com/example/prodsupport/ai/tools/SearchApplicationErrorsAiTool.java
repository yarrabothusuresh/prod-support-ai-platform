package com.example.prodsupport.ai.tools;

import com.example.prodsupport.ai.tools.audit.ToolExecutionAudit;
import com.example.prodsupport.ai.tools.audit.ToolExecutionAuditor;
import com.example.prodsupport.ai.tools.context.InvestigationContext;
import com.example.prodsupport.ai.tools.context.InvestigationContextHolder;
import com.example.prodsupport.ai.tools.model.SearchApplicationErrorsRequest;
import com.example.prodsupport.ai.tools.model.ToolExecutionResult;
import com.example.prodsupport.ai.tools.security.ApplicationAccessValidator;
import com.example.prodsupport.ai.tools.security.ToolAllowlist;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.logging.model.LogEntryDto;
import com.example.prodsupport.logging.model.LogSearchResult;
import com.example.prodsupport.logging.service.LogSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;

@Component
public class SearchApplicationErrorsAiTool implements Function<SearchApplicationErrorsRequest, ToolExecutionResult<LogSearchResult>> {

    private static final Logger log = LoggerFactory.getLogger(SearchApplicationErrorsAiTool.class);

    public static final String TOOL_NAME = ToolAllowlist.TOOL_SEARCH_APPLICATION_ERRORS;
    public static final String TOOL_DESCRIPTION = "Search recent ERROR and WARN logs for a registered application. " +
            "Use this tool when investigating exceptions, failures, HTTP errors, database errors, Kafka errors or production incidents. " +
            "Search is restricted by application, environment, time window, and configured result limits. " +
            "The tool does not provide arbitrary Elasticsearch access.";

    private final ApplicationAccessValidator accessValidator;
    private final LogSearchService logSearchService;
    private final ToolExecutionAuditor auditor;

    public SearchApplicationErrorsAiTool(ApplicationAccessValidator accessValidator,
                                         LogSearchService logSearchService,
                                         ToolExecutionAuditor auditor) {
        this.accessValidator = accessValidator;
        this.logSearchService = logSearchService;
        this.auditor = auditor;
    }

    @Override
    public ToolExecutionResult<LogSearchResult> apply(SearchApplicationErrorsRequest request) {
        Instant startTime = Instant.now();
        InvestigationContext context = InvestigationContextHolder.getContext();

        if (context != null) {
            context.recordToolRequested(TOOL_NAME);
            if (!context.canExecuteTool()) {
                String warning = "Investigation reached maximum diagnostic steps. Answer is based on currently available evidence.";
                log.warn("Maximum tool call limit reached ({} calls). Aborting {}", context.getMaxToolCalls(), TOOL_NAME);
                context.recordWarning(warning);
                ToolExecutionResult<LogSearchResult> limitResult = ToolExecutionResult.failure(TOOL_NAME, warning, startTime, 0);
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
            ToolExecutionResult<LogSearchResult> invalidResult = ToolExecutionResult.failure(TOOL_NAME, warning, startTime, 0);
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
            ToolExecutionResult<LogSearchResult> authResult = ToolExecutionResult.failure(TOOL_NAME, failureReason, startTime, duration);
            if (context != null) {
                context.recordToolExecution(authResult);
            }
            return authResult;
        }

        int minutes = request.minutes() != null && request.minutes() > 0 ? request.minutes() : 15;
        Instant end = Instant.now();
        Instant start = end.minus(Duration.ofMinutes(minutes));

        try {
            LogSearchResult result = logSearchService.searchLogs(
                    app.getApplicationName(),
                    app.getEnvironment(),
                    start,
                    end,
                    List.of("ERROR", "WARN"),
                    request.keyword(),
                    null,
                    20
            );

            long duration = Duration.between(startTime, Instant.now()).toMillis();
            auditor.audit(new ToolExecutionAudit(TOOL_NAME, app.getApplicationName(), app.getEnvironment(),
                    startTime, Instant.now(), duration, true, null));
            ToolExecutionResult<LogSearchResult> successResult = ToolExecutionResult.success(TOOL_NAME, result, startTime, duration);

            if (context != null) {
                context.recordToolExecution(successResult);
                if (result.totalHits() == 0) {
                    context.recordEvidence("Centralized logs: 0 error/warn events found in Elasticsearch over last " + minutes + "m");
                } else {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Centralized logs: ").append(result.totalHits()).append(" error/warn events found in Elasticsearch over last ")
                            .append(minutes).append("m. Sample errors: ");
                    int count = Math.min(3, result.logs().size());
                    for (int i = 0; i < count; i++) {
                        LogEntryDto l = result.logs().get(i);
                        if (i > 0) sb.append("; ");
                        sb.append("[").append(l.errorType() != null ? l.errorType() : "Error").append(": ")
                                .append(l.message()).append("]");
                    }
                    if (result.truncated()) {
                        sb.append(" [results truncated at ").append(result.logs().size()).append("]");
                    }
                    context.recordEvidence(sb.toString());
                }
            }

            return successResult;

        } catch (Exception ex) {
            long duration = Duration.between(startTime, Instant.now()).toMillis();
            String errorMsg = ex.getMessage();
            String warning = "Centralized log search currently unavailable (" + errorMsg + ")";
            auditor.audit(new ToolExecutionAudit(TOOL_NAME, app.getApplicationName(), app.getEnvironment(),
                    startTime, Instant.now(), duration, false, errorMsg));
            ToolExecutionResult<LogSearchResult> failResult = ToolExecutionResult.failure(TOOL_NAME, warning, startTime, duration);
            if (context != null) {
                context.recordToolExecution(failResult);
                context.recordWarning(warning);
            }
            return failResult;
        }
    }
}
