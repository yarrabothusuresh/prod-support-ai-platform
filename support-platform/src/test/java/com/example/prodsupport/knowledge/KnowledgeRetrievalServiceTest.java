package com.example.prodsupport.knowledge;

import com.example.prodsupport.ai.config.KnowledgeProperties;
import com.example.prodsupport.domain.DocumentType;
import com.example.prodsupport.knowledge.model.KnowledgeEvidence;
import com.example.prodsupport.knowledge.model.KnowledgeSource;
import com.example.prodsupport.knowledge.service.KnowledgeRetrievalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeRetrievalServiceTest {

    private VectorStore vectorStore;
    private KnowledgeProperties properties;
    private KnowledgeRetrievalService retrievalService;

    @BeforeEach
    void setUp() {
        vectorStore = mock(VectorStore.class);
        properties = new KnowledgeProperties();
        properties.setDefaultTopK(5);
        properties.setMaxTopK(10);
        properties.setMaxContextChars(500);
        retrievalService = new KnowledgeRetrievalService(vectorStore, properties);
    }

    @Test
    @DisplayName("Filters documents strictly by applicationName and environment")
    void filtersByAppAndEnv() {
        Document matchingDoc = new Document("Matching content", Map.of(
                "applicationName", "payment-service",
                "environment", "local",
                "documentType", "RUNBOOK",
                "title", "Payment Runbook",
                "source", "runbooks/payment.md"
        ));

        Document otherAppDoc = new Document("Other app content", Map.of(
                "applicationName", "order-service",
                "environment", "local",
                "documentType", "RUNBOOK",
                "title", "Order Runbook",
                "source", "runbooks/order.md"
        ));

        Document otherEnvDoc = new Document("Other env content", Map.of(
                "applicationName", "payment-service",
                "environment", "prod",
                "documentType", "RUNBOOK",
                "title", "Prod Runbook",
                "source", "runbooks/prod.md"
        ));

        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(matchingDoc, otherAppDoc, otherEnvDoc));

        List<KnowledgeEvidence> results = retrievalService.retrieve(
                "payment-service", "local", "database failure", null, 5);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().content()).isEqualTo("Matching content");
        assertThat(results.getFirst().metadata().get("applicationName")).isEqualTo("payment-service");
    }

    @Test
    @DisplayName("Filters by document type if specified")
    void filtersByDocumentType() {
        Document runbookDoc = new Document("Runbook steps", Map.of(
                "applicationName", "payment-service",
                "environment", "local",
                "documentType", "RUNBOOK",
                "title", "Runbook Title",
                "source", "runbooks/payment.md"
        ));

        Document incidentDoc = new Document("Past Incident RCA", Map.of(
                "applicationName", "payment-service",
                "environment", "local",
                "documentType", "INCIDENT",
                "title", "Incident 001",
                "source", "incidents/inc-001.md"
        ));

        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(runbookDoc, incidentDoc));

        List<KnowledgeEvidence> incidentOnly = retrievalService.retrieve(
                "payment-service", "local", "timeout", List.of(DocumentType.INCIDENT), 5);

        assertThat(incidentOnly).hasSize(1);
        assertThat(incidentOnly.getFirst().documentType()).isEqualTo("INCIDENT");
    }

    @Test
    @DisplayName("Deduplicates sources from multiple chunks of same document")
    void deduplicatesSources() {
        KnowledgeEvidence chunk1 = new KnowledgeEvidence(
                "doc-1", "Payment Runbook", "RUNBOOK", "runbooks/payment.md", "Chunk 1 content", 0.9,
                Map.of("applicationName", "payment-service", "environment", "local"));

        KnowledgeEvidence chunk2 = new KnowledgeEvidence(
                "doc-1", "Payment Runbook", "RUNBOOK", "runbooks/payment.md", "Chunk 2 content", 0.85,
                Map.of("applicationName", "payment-service", "environment", "local"));

        KnowledgeEvidence otherDoc = new KnowledgeEvidence(
                "doc-2", "Architecture Spec", "ARCHITECTURE", "architecture/payment.md", "Arch content", 0.8,
                Map.of("applicationName", "payment-service", "environment", "local"));

        List<KnowledgeSource> sources = retrievalService.extractDeduplicatedSources(List.of(chunk1, chunk2, otherDoc));

        assertThat(sources).hasSize(2);
        assertThat(sources.stream().map(KnowledgeSource::title))
                .containsExactly("Payment Runbook", "Architecture Spec");
    }
}
