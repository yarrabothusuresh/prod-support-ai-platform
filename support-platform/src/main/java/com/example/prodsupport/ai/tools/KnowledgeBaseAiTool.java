package com.example.prodsupport.ai.tools;

import com.example.prodsupport.ai.tools.audit.ToolExecutionAudit;
import com.example.prodsupport.ai.tools.audit.ToolExecutionAuditor;
import com.example.prodsupport.ai.tools.context.InvestigationContext;
import com.example.prodsupport.ai.tools.context.InvestigationContextHolder;
import com.example.prodsupport.ai.tools.model.KnowledgeSearchResultData;
import com.example.prodsupport.ai.tools.model.KnowledgeSearchToolRequest;
import com.example.prodsupport.ai.tools.model.ToolExecutionResult;
import com.example.prodsupport.ai.tools.security.ApplicationAccessValidator;
import com.example.prodsupport.ai.tools.security.ToolAllowlist;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.knowledge.model.KnowledgeEvidence;
import com.example.prodsupport.knowledge.model.KnowledgeSource;
import com.example.prodsupport.knowledge.service.KnowledgeRetrievalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;

@Component
public class KnowledgeBaseAiTool implements Function<KnowledgeSearchToolRequest, ToolExecutionResult<KnowledgeSearchResultData>> {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseAiTool.class);

    public static final String TOOL_NAME = ToolAllowlist.TOOL_SEARCH_KNOWLEDGE_BASE;
    public static final String TOOL_DESCRIPTION = "Search approved production support knowledge base documentation such as runbooks, " +
            "architecture documents, historical incidents, RCAs, troubleshooting guides, and operational procedures for the registered application.";

    private final ApplicationAccessValidator accessValidator;
    private final KnowledgeRetrievalService retrievalService;
    private final ToolExecutionAuditor auditor;

    public KnowledgeBaseAiTool(ApplicationAccessValidator accessValidator,
                              KnowledgeRetrievalService retrievalService,
                              ToolExecutionAuditor auditor) {
        this.accessValidator = accessValidator;
        this.retrievalService = retrievalService;
        this.auditor = auditor;
    }

    @Override
    public ToolExecutionResult<KnowledgeSearchResultData> apply(KnowledgeSearchToolRequest request) {
        Instant startTime = Instant.now();
        InvestigationContext context = InvestigationContextHolder.getContext();

        if (context != null) {
            context.recordToolRequested(TOOL_NAME);
            if (!context.canExecuteTool()) {
                String warning = "Investigation reached maximum diagnostic steps. Answer is based on currently available evidence.";
                log.warn("Maximum tool call limit reached ({} calls). Aborting {}", context.getMaxToolCalls(), TOOL_NAME);
                context.recordWarning(warning);
                ToolExecutionResult<KnowledgeSearchResultData> limitResult = ToolExecutionResult.failure(TOOL_NAME, warning, startTime, 0);
                context.recordToolExecution(limitResult);
                return limitResult;
            }
            context.incrementExecutionCount();
        }

        if (request == null || request.applicationName() == null || request.applicationName().isBlank()
                || request.environment() == null || request.environment().isBlank()
                || request.query() == null || request.query().isBlank()) {
            String warning = "Invalid parameters: applicationName, environment, and query must not be blank";
            auditor.audit(new ToolExecutionAudit(TOOL_NAME,
                    request != null ? request.applicationName() : "unknown",
                    request != null ? request.environment() : "unknown",
                    startTime, Instant.now(), 0, false, warning));
            ToolExecutionResult<KnowledgeSearchResultData> result = ToolExecutionResult.failure(TOOL_NAME, warning, startTime, 0);
            if (context != null) {
                context.recordToolExecution(result);
            }
            return result;
        }

        // Validate application access (anti-SSRF and app validation)
        RegisteredApplication app;
        try {
            app = accessValidator.validateAndGet(request.applicationName(), request.environment());
        } catch (Exception e) {
            long duration = Duration.between(startTime, Instant.now()).toMillis();
            String errorMsg = "Application validation failed: " + e.getMessage();
            auditor.audit(new ToolExecutionAudit(TOOL_NAME, request.applicationName(), request.environment(),
                    startTime, Instant.now(), duration, false, errorMsg));
            ToolExecutionResult<KnowledgeSearchResultData> result = ToolExecutionResult.failure(TOOL_NAME, errorMsg, startTime, duration);
            if (context != null) {
                context.recordToolExecution(result);
            }
            return result;
        }

        // Search knowledge base
        try {
            int topK = retrievalService.sanitizeTopK(request.topK());
            List<KnowledgeEvidence> matches = retrievalService.retrieve(
                    app.getApplicationName(),
                    app.getEnvironment(),
                    request.query(),
                    null, // search all types by default
                    topK
            );

            List<KnowledgeSource> sources = retrievalService.extractDeduplicatedSources(matches);
            KnowledgeSearchResultData data = new KnowledgeSearchResultData(
                    app.getApplicationName(),
                    app.getEnvironment(),
                    request.query(),
                    matches.size(),
                    matches,
                    sources
            );

            long duration = Duration.between(startTime, Instant.now()).toMillis();
            auditor.audit(new ToolExecutionAudit(TOOL_NAME, request.applicationName(), request.environment(),
                    startTime, Instant.now(), duration, true, "Retrieved " + matches.size() + " matches"));

            ToolExecutionResult<KnowledgeSearchResultData> result = ToolExecutionResult.success(TOOL_NAME, data, startTime, duration);
            if (context != null) {
                context.recordToolExecution(result);
                context.putEvidence("knowledgeSearchResult", data);
                context.recordEvidence("Found " + matches.size() + " knowledge base documents matching query '" + request.query() + "'");
            }
            return result;

        } catch (Exception e) {
            long duration = Duration.between(startTime, Instant.now()).toMillis();
            String errorMsg = "Failed to search knowledge base: " + e.getMessage();
            log.error("Knowledge search error in tool for app '{}': {}", request.applicationName(), e.getMessage(), e);
            auditor.audit(new ToolExecutionAudit(TOOL_NAME, request.applicationName(), request.environment(),
                    startTime, Instant.now(), duration, false, errorMsg));
            ToolExecutionResult<KnowledgeSearchResultData> result = ToolExecutionResult.failure(TOOL_NAME, errorMsg, startTime, duration);
            if (context != null) {
                context.recordToolExecution(result);
            }
            return result;
        }
    }
}
