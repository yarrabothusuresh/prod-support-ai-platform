package com.example.prodsupport.application.service;

import com.example.prodsupport.ai.client.OllamaSupportAiClient;
import com.example.prodsupport.ai.config.AiProperties;
import com.example.prodsupport.ai.model.ApplicationSupportContext;
import com.example.prodsupport.ai.model.SupportAiResult;
import com.example.prodsupport.ai.model.SupportDependencyDto;
import com.example.prodsupport.ai.model.SupportErrorDto;
import com.example.prodsupport.ai.prompt.SupportPromptBuilder;
import com.example.prodsupport.ai.tools.DiagnosticToolRegistry;
import com.example.prodsupport.ai.tools.audit.ToolExecutionAuditor;
import com.example.prodsupport.ai.tools.context.InvestigationContext;
import com.example.prodsupport.ai.tools.context.InvestigationContextHolder;
import com.example.prodsupport.ai.tools.model.*;
import com.example.prodsupport.ai.tools.security.ApplicationAccessValidator;
import com.example.prodsupport.ai.tools.security.ToolAllowlist;
import com.example.prodsupport.application.dto.SupportInvestigationRequest;
import com.example.prodsupport.application.dto.SupportInvestigationResponse;
import com.example.prodsupport.common.exception.AiServiceUnavailableException;
import com.example.prodsupport.domain.RegisteredApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class InvestigationService {

    private static final Logger log = LoggerFactory.getLogger(InvestigationService.class);
    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)\\s*```", Pattern.CASE_INSENSITIVE);

    private final ApplicationAccessValidator accessValidator;
    private final DiagnosticToolRegistry toolRegistry;
    private final DiagnosticService diagnosticService;
    private final com.example.prodsupport.application.service.kafka.KafkaDiagnosticService kafkaDiagnosticService;
    private final com.example.prodsupport.database.service.ApplicationDatabaseDiagnosticService databaseDiagnosticService;
    private final com.example.prodsupport.logging.service.LogSearchService logSearchService;
    private final com.example.prodsupport.logging.service.ErrorPatternService errorPatternService;
    private final com.example.prodsupport.logging.client.LogSearchClient logSearchClient;
    private final ChatModel chatModel;
    private final SupportPromptBuilder promptBuilder;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final ToolExecutionAuditor auditor;

    public InvestigationService(ApplicationAccessValidator accessValidator,
                                DiagnosticToolRegistry toolRegistry,
                                DiagnosticService diagnosticService,
                                com.example.prodsupport.application.service.kafka.KafkaDiagnosticService kafkaDiagnosticService,
                                com.example.prodsupport.database.service.ApplicationDatabaseDiagnosticService databaseDiagnosticService,
                                com.example.prodsupport.logging.service.LogSearchService logSearchService,
                                com.example.prodsupport.logging.service.ErrorPatternService errorPatternService,
                                com.example.prodsupport.logging.client.LogSearchClient logSearchClient,
                                ChatModel chatModel,
                                SupportPromptBuilder promptBuilder,
                                AiProperties aiProperties,
                                ObjectMapper objectMapper,
                                ToolExecutionAuditor auditor) {
        this.accessValidator = accessValidator;
        this.toolRegistry = toolRegistry;
        this.diagnosticService = diagnosticService;
        this.kafkaDiagnosticService = kafkaDiagnosticService;
        this.databaseDiagnosticService = databaseDiagnosticService;
        this.logSearchService = logSearchService;
        this.errorPatternService = errorPatternService;
        this.logSearchClient = logSearchClient;
        this.chatModel = chatModel;
        this.promptBuilder = promptBuilder;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.auditor = auditor;
    }



    public SupportInvestigationResponse investigate(SupportInvestigationRequest request) {
        String appName = request.applicationName().trim();
        String environment = request.environment().trim();
        String question = request.question().trim();
        String mode = request.mode() != null ? request.mode().trim().toUpperCase() : "AGENTIC";

        log.info("Starting investigation for '{}/{}' with mode '{}': '{}'", appName, environment, mode, question);

        // 1. Validate application exists and is enabled
        RegisteredApplication app = accessValidator.validateAndGet(appName, environment);

        // 2. Select execution mode
        boolean useAgentic = "AGENTIC".equalsIgnoreCase(mode) && aiProperties.getToolCalling().isEnabled();

        if (useAgentic) {
            try {
                return executeAgenticInvestigation(app, question);
            } catch (Exception ex) {
                log.warn("Agentic tool-calling investigation failed: {}.", ex.getMessage());
                if (aiProperties.getToolCalling().isDeterministicFallback()) {
                    log.info("Executing deterministic fallback investigation for '{}/{}'", appName, environment);
                    return executeDeterministicInvestigation(app, question, "Agentic tool calling unavailable or failed; completed via deterministic fallback (" + cleanErrorMessage(ex) + ")");
                }
                throw ex instanceof RuntimeException re ? re : new AiServiceUnavailableException("Investigation failed", ex);
            }
        } else {
            return executeDeterministicInvestigation(app, question, null);
        }
    }

    private SupportInvestigationResponse executeAgenticInvestigation(RegisteredApplication app, String question) {
        String appName = app.getApplicationName();
        String environment = app.getEnvironment();
        int maxToolCalls = aiProperties.getMaxToolCalls();

        InvestigationContext context = new InvestigationContext(appName, environment, question, maxToolCalls);
        InvestigationContextHolder.setContext(context);

        try {
            String systemPrompt = promptBuilder.buildAgenticInvestigationSystemPrompt();
            String userPrompt = promptBuilder.buildAgenticInvestigationUserPrompt(appName, environment, question);

            if (aiProperties.isLogPrompt()) {
                log.debug("Agentic System Prompt:\n{}", systemPrompt);
                log.debug("Agentic User Prompt:\n{}", userPrompt);
            }

            List<Message> messages = List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage(userPrompt)
            );

            OllamaOptions options = OllamaOptions.create()
                    .withModel(aiProperties.getModel())
                    .withTemperature(0.1)
                    .withFunctionCallbacks(toolRegistry.getAllCallbacks());

            Prompt prompt = new Prompt(messages, options);
            ChatResponse chatResponse = chatModel.call(prompt);

            if (chatResponse == null || chatResponse.getResult() == null || chatResponse.getResult().getOutput() == null) {
                throw new AiServiceUnavailableException("AI model returned empty response during agentic investigation");
            }

            String responseContent = chatResponse.getResult().getOutput().getContent();
            log.info("Agentic AI investigation completed for '{}/{}'. Executed {} tools: {}",
                    appName, environment, context.getExecutionCount(), context.getToolsUsed());

            return buildResponseFromContent(responseContent, app, context);

        } finally {
            InvestigationContextHolder.clearContext();
        }
    }

    private SupportInvestigationResponse executeDeterministicInvestigation(RegisteredApplication app, String question, String fallbackNotice) {
        String appName = app.getApplicationName();
        String environment = app.getEnvironment();
        List<String> warnings = new ArrayList<>();
        if (fallbackNotice != null) {
            warnings.add(fallbackNotice);
        }

        List<String> toolsUsed = new ArrayList<>();
        List<String> observedFacts = new ArrayList<>();

        // 1. Info
        ApplicationInfoData appInfo = null;
        try {
            appInfo = diagnosticService.getApplicationInfo(app);
            toolsUsed.add(ToolAllowlist.TOOL_GET_APPLICATION_INFO);
            observedFacts.add("Application " + appName + " is registered for team '" + app.getTeam() + "' (enabled=" + app.isEnabled() + ")");
        } catch (Exception ex) {
            warnings.add("Unable to retrieve application info: " + diagnosticService.cleanErrorMessage(ex));
        }

        // 2. Health
        ApplicationHealthData health = null;
        try {
            health = diagnosticService.checkHealth(app);
            toolsUsed.add(ToolAllowlist.TOOL_CHECK_APPLICATION_HEALTH);
            observedFacts.add("Application health status is " + health.status() + " (source: /actuator/health)");
        } catch (Exception ex) {
            warnings.add("Unable to retrieve actuator health (" + diagnosticService.cleanErrorMessage(ex) + ")");
        }

        // 3. Errors
        RecentErrorsData errorsData = null;
        try {
            errorsData = diagnosticService.getRecentErrors(app, 10);
            toolsUsed.add(ToolAllowlist.TOOL_GET_RECENT_ERRORS);
            if (errorsData.count() == 0) {
                observedFacts.add("No recent application errors recorded (count=0)");
            } else {
                observedFacts.add(errorsData.count() + " recent errors were found in the application error store");
            }
        } catch (Exception ex) {
            warnings.add("Unable to retrieve recent errors (" + diagnosticService.cleanErrorMessage(ex) + ")");
        }

        // 4. Dependencies
        DependenciesData depsData = null;
        try {
            depsData = diagnosticService.checkDependencies(app);
            toolsUsed.add(ToolAllowlist.TOOL_CHECK_DEPENDENCIES);
            if (depsData.dependencies().isEmpty()) {
                observedFacts.add("No downstream dependencies configured");
            } else {
                for (DependencyItemDto dep : depsData.dependencies()) {
                    observedFacts.add("Dependency '" + dep.name() + "' (" + dep.type() + ") is " + dep.status());
                }
            }
        } catch (Exception ex) {
            warnings.add("Unable to retrieve dependencies (" + diagnosticService.cleanErrorMessage(ex) + ")");
        }

        // 5. Kafka Diagnostics (if configured)
        if (kafkaDiagnosticService.isKafkaConfiguredAndEnabled(app)) {
            try {
                var summary = kafkaDiagnosticService.getDiagnosticSummary(app);
                toolsUsed.add(ToolAllowlist.TOOL_CHECK_KAFKA_CLUSTER);
                if (summary.clusterReachable()) {
                    observedFacts.add("Kafka cluster is reachable (brokerCount=" + summary.brokerCount() + ")");
                } else {
                    observedFacts.add("Kafka cluster is unreachable");
                }
                for (var cg : summary.consumerGroups()) {
                    toolsUsed.add(ToolAllowlist.TOOL_CHECK_KAFKA_CONSUMER_LAG);
                    observedFacts.add("Kafka consumer group '" + cg.consumerGroup() + "' (state=" + cg.state() +
                            ") lag is " + (cg.totalLag() != null ? cg.totalLag() : "unknown") + " [status: " + cg.status() + "]");
                }
                if (summary.warnings() != null) {
                    warnings.addAll(summary.warnings());
                }
            } catch (Exception ex) {
                warnings.add("Kafka diagnostic failed: " + ex.getMessage());
            }
        }

        // 6. Database Diagnostics (if configured)
        try {
            var dbDiag = databaseDiagnosticService.runDiagnostics(app);
            if (dbDiag.enabled()) {
                if (dbDiag.health() != null) {
                    toolsUsed.add(ToolAllowlist.TOOL_CHECK_DATABASE_HEALTH);
                    observedFacts.add("Database '" + dbDiag.databaseName() + "' (" + dbDiag.databaseType() + ") status is " +
                            dbDiag.health().status() + " (responseTime=" + dbDiag.health().responseTimeMs() + "ms)");
                }
                if (dbDiag.connectionPool() != null && !"UNKNOWN".equalsIgnoreCase(dbDiag.connectionPool().status())) {
                    toolsUsed.add(ToolAllowlist.TOOL_CHECK_DATABASE_CONNECTION_POOL);
                    var pool = dbDiag.connectionPool();
                    observedFacts.add("Connection pool '" + pool.poolName() + "' is " + pool.status() +
                            ": active=" + pool.activeConnections() + "/" + pool.maxPoolSize() + " (" + pool.utilizationPercent() +
                            "%), waitingThreads=" + pool.threadsAwaitingConnection());
                }
                if (dbDiag.activity() != null && dbDiag.activity().activeSessions() > 0) {
                    toolsUsed.add(ToolAllowlist.TOOL_CHECK_DATABASE_ACTIVITY);
                    var act = dbDiag.activity();
                    observedFacts.add("Database activity: " + act.activeSessions() + " active sessions, " +
                            act.waitingSessions() + " waiting sessions, " + act.longRunningQueryCount() + " long-running queries");
                }
                if (dbDiag.warnings() != null) {
                    warnings.addAll(dbDiag.warnings());
                }
            }
        } catch (Exception ex) {
            warnings.add("Database diagnostic failed: " + ex.getMessage());
        }

        // 7. Centralized Log Diagnostics (Elasticsearch)
        try {

            if (logSearchClient != null && logSearchClient.isAvailable()) {
                Instant end = Instant.now();
                Instant start = end.minus(java.time.Duration.ofMinutes(15));
                var logResult = logSearchService.searchLogs(appName, environment, start, end, List.of("ERROR", "WARN"), null, null, 10);
                toolsUsed.add(ToolAllowlist.TOOL_SEARCH_APPLICATION_ERRORS);
                if (logResult.totalHits() > 0) {
                    observedFacts.add("Centralized logs: " + logResult.totalHits() + " error/warn events found in Elasticsearch over last 15m");
                }
                var patterns = errorPatternService.summarizeErrors(appName, environment, 15, 10);
                toolsUsed.add(ToolAllowlist.TOOL_GET_ERROR_PATTERN_SUMMARY);
                if (!patterns.patterns().isEmpty()) {
                    StringBuilder psb = new StringBuilder("Recurring error patterns (last 15m): ");
                    for (int i = 0; i < patterns.patterns().size(); i++) {
                        var p = patterns.patterns().get(i);
                        if (i > 0) psb.append(", ");
                        psb.append(p.errorType()).append("=").append(p.count());
                    }
                    observedFacts.add(psb.toString());
                }
                if (logResult.warnings() != null) {
                    warnings.addAll(logResult.warnings());
                }
            } else {
                warnings.add("Centralized log search is currently unavailable. Evaluated application using in-memory diagnostics.");
            }
        } catch (Exception ex) {
            warnings.add("Centralized log diagnostic unavailable: " + ex.getMessage());
        }

        // Build ApplicationSupportContext for model prompting

        List<SupportErrorDto> errorDtos = new ArrayList<>();
        if (errorsData != null && errorsData.errors() != null) {
            for (SanitizedErrorDto e : errorsData.errors()) {
                Instant ts = Instant.now();
                if (e.timestamp() != null) {
                    try {
                        ts = Instant.parse(e.timestamp());
                    } catch (Exception ignored) {}
                }
                errorDtos.add(new SupportErrorDto(ts, "ERROR", e.type(), e.message()));
            }
        }

        List<SupportDependencyDto> depDtos = new ArrayList<>();
        if (depsData != null && depsData.dependencies() != null) {
            for (DependencyItemDto d : depsData.dependencies()) {
                depDtos.add(new SupportDependencyDto(d.name(), d.type(), d.status()));
            }
        }

        ApplicationSupportContext supportContext = new ApplicationSupportContext(
                appName,
                app.getTeam(),
                environment,
                app.getDescription(),
                app.getBaseUrl(),
                "UP",
                health != null ? health.status() : "UNKNOWN",
                appInfo != null,
                health != null,
                OffsetDateTime.now(),
                warnings,
                errorDtos,
                depDtos,
                errorsData != null,
                depsData != null
        );

        String systemPrompt = promptBuilder.buildSystemPrompt();
        String userPrompt = promptBuilder.buildUserPrompt(supportContext, question);

        String rawContent = "";
        try {
            Prompt prompt = new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(userPrompt)));
            ChatResponse chatResponse = chatModel.call(prompt);
            if (chatResponse != null && chatResponse.getResult() != null && chatResponse.getResult().getOutput() != null) {
                rawContent = chatResponse.getResult().getOutput().getContent();
            }
        } catch (Exception ex) {
            log.warn("Deterministic AI prompt failed: {}. Generating synthetic diagnosis from evidence.", ex.getMessage());
        }

        return buildResponseFromContent(rawContent, app, observedFacts, toolsUsed, warnings);
    }

    private SupportInvestigationResponse buildResponseFromContent(String rawContent, RegisteredApplication app, InvestigationContext context) {
        List<String> realToolsUsed = context.getToolsUsed();
        int realExecutionCount = context.getExecutionCount();
        List<String> combinedWarnings = new ArrayList<>(context.getWarnings());

        ParsedPayload parsed = parsePayload(rawContent);

        List<String> facts = parsed.observedFacts != null && !parsed.observedFacts.isEmpty()
                ? parsed.observedFacts
                : context.getEvidence();

        if (facts.isEmpty()) {
            facts = List.of("Diagnostic tools executed: " + String.join(", ", realToolsUsed));
        }

        String summary = parsed.summary != null && !parsed.summary.isBlank()
                ? parsed.summary
                : "Investigation complete for " + app.getApplicationName();

        List<String> likelyCauses = parsed.likelyCauses != null && !parsed.likelyCauses.isEmpty()
                ? parsed.likelyCauses
                : List.of("Assessed based on available live diagnostic evidence");

        List<String> recommendedChecks = parsed.recommendedChecks != null && !parsed.recommendedChecks.isEmpty()
                ? parsed.recommendedChecks
                : List.of("Review service logs and verify downstream dependency states");

        String confidence = parsed.confidence != null ? parsed.confidence.toUpperCase() : "MEDIUM";

        List<com.example.prodsupport.knowledge.model.KnowledgeSource> sources = new ArrayList<>();
        Object knowledgeEvidenceObj = context.getGatheredEvidence().get("knowledgeSearchResult");
        if (knowledgeEvidenceObj instanceof com.example.prodsupport.ai.tools.model.KnowledgeSearchResultData searchData) {
            if (searchData.sources() != null) {
                sources.addAll(searchData.sources());
            }
        }

        List<String> guidance = parsed.knowledgeGuidance != null ? parsed.knowledgeGuidance : Collections.emptyList();

        return new SupportInvestigationResponse(
                app.getApplicationName(),
                app.getEnvironment(),
                summary,
                facts,
                likelyCauses,
                recommendedChecks,
                guidance,
                sources,
                confidence,
                realToolsUsed,
                realExecutionCount,
                combinedWarnings
        );
    }

    private SupportInvestigationResponse buildResponseFromContent(String rawContent, RegisteredApplication app,
                                                                   List<String> observedFacts, List<String> toolsUsed,
                                                                   List<String> warnings) {
        ParsedPayload parsed = parsePayload(rawContent);

        String summary = parsed.summary != null && !parsed.summary.isBlank()
                ? parsed.summary
                : "Deterministic investigation complete for " + app.getApplicationName();

        List<String> facts = (parsed.observedFacts != null && !parsed.observedFacts.isEmpty())
                ? parsed.observedFacts
                : observedFacts;

        List<String> likelyCauses = (parsed.likelyCauses != null && !parsed.likelyCauses.isEmpty())
                ? parsed.likelyCauses
                : List.of("Evaluated from gathered diagnostic telemetry");

        List<String> recommendedChecks = (parsed.recommendedChecks != null && !parsed.recommendedChecks.isEmpty())
                ? parsed.recommendedChecks
                : List.of("Verify dependency availability", "Check recent logs");

        String confidence = parsed.confidence != null ? parsed.confidence.toUpperCase() : "MEDIUM";

        return new SupportInvestigationResponse(
                app.getApplicationName(),
                app.getEnvironment(),
                summary,
                facts,
                likelyCauses,
                recommendedChecks,
                confidence,
                toolsUsed,
                toolsUsed.size(),
                warnings
        );
    }

    private ParsedPayload parsePayload(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return new ParsedPayload();
        }

        String json = extractJson(rawContent.trim());
        try {
            return objectMapper.readValue(json, ParsedPayload.class);
        } catch (Exception ex) {
            log.debug("Could not parse LLM output as JSON: {}. Using content as summary.", ex.getMessage());
            ParsedPayload fallback = new ParsedPayload();
            fallback.summary = rawContent.length() > 300 ? rawContent.substring(0, 300) + "..." : rawContent;
            return fallback;
        }
    }

    private String extractJson(String text) {
        Matcher matcher = JSON_BLOCK_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1).trim();
        }
        return text;
    }

    private String cleanErrorMessage(Exception ex) {
        String msg = ex.getMessage();
        return msg != null && !msg.isBlank() ? msg : ex.getClass().getSimpleName();
    }

    public static class ParsedPayload {
        public String summary;
        public List<String> observedFacts = new ArrayList<>();
        public List<String> likelyCauses = new ArrayList<>();
        public List<String> recommendedChecks = new ArrayList<>();
        public List<String> knowledgeGuidance = new ArrayList<>();
        public String confidence = "MEDIUM";
    }
}
