package com.example.prodsupport.knowledge;

import com.example.prodsupport.ai.tools.KnowledgeBaseAiTool;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeBaseAiToolTest {

    private ApplicationAccessValidator accessValidator;
    private KnowledgeRetrievalService retrievalService;
    private ToolExecutionAuditor auditor;
    private KnowledgeBaseAiTool tool;

    @BeforeEach
    void setUp() {
        accessValidator = mock(ApplicationAccessValidator.class);
        retrievalService = mock(KnowledgeRetrievalService.class);
        auditor = new ToolExecutionAuditor();
        tool = new KnowledgeBaseAiTool(accessValidator, retrievalService, auditor);
    }

    @AfterEach
    void tearDown() {
        InvestigationContextHolder.clearContext();
    }

    @Test
    @DisplayName("Tool name matches allowlist entry and is allowed")
    void toolNameMatchesAllowlist() {
        assertThat(KnowledgeBaseAiTool.TOOL_NAME).isEqualTo("search_knowledge_base");
        assertThat(ToolAllowlist.isAllowed(KnowledgeBaseAiTool.TOOL_NAME)).isTrue();
    }

    @Test
    @DisplayName("Executes knowledge search and records evidence in investigation context")
    void executesKnowledgeSearchSuccessfully() {
        RegisteredApplication app = new RegisteredApplication(
                "payment-service", "payments", "local", "Payment Service",
                "http://localhost:8081", "http://localhost:8081/actuator/health", "http://localhost:8081/support/info", true);

        when(accessValidator.validateAndGet("payment-service", "local")).thenReturn(app);
        when(retrievalService.sanitizeTopK(any())).thenReturn(3);

        KnowledgeEvidence evidence = new KnowledgeEvidence(
                "doc-1", "DB Runbook", "RUNBOOK", "runbooks/db.md", "Database recovery steps", 0.95,
                Map.of("applicationName", "payment-service", "environment", "local"));
        KnowledgeSource source = new KnowledgeSource("DB Runbook", "RUNBOOK", "runbooks/db.md");

        when(retrievalService.retrieve(eq("payment-service"), eq("local"), eq("database error"), isNull(), eq(3)))
                .thenReturn(List.of(evidence));
        when(retrievalService.extractDeduplicatedSources(any())).thenReturn(List.of(source));

        InvestigationContext context = new InvestigationContext("payment-service", "local", "Investigate db", 5);
        InvestigationContextHolder.setContext(context);

        KnowledgeSearchToolRequest request = new KnowledgeSearchToolRequest("payment-service", "local", "database error", 3);
        ToolExecutionResult<KnowledgeSearchResultData> result = tool.apply(request);

        assertThat(result.success()).isTrue();
        assertThat(result.data()).isNotNull();
        assertThat(result.data().count()).isEqualTo(1);
        assertThat(result.data().matches()).hasSize(1);
        assertThat(result.data().sources()).hasSize(1);

        // Context check
        assertThat(context.getExecutionCount()).isEqualTo(1);
        assertThat(context.getEvidence()).isNotEmpty();
        assertThat(context.getGatheredEvidence()).containsKey("knowledgeSearchResult");

        // Audit check
        assertThat(auditor.getAuditHistory()).hasSize(1);
        assertThat(auditor.getAuditHistory().getFirst().toolName()).isEqualTo("search_knowledge_base");
        assertThat(auditor.getAuditHistory().getFirst().success()).isTrue();
    }
}
