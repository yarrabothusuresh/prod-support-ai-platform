package com.example.prodsupport.knowledge;

import com.example.prodsupport.domain.DocumentType;
import com.example.prodsupport.domain.KnowledgeDocument;
import com.example.prodsupport.knowledge.dto.KnowledgeBulkIngestResponse;
import com.example.prodsupport.knowledge.dto.KnowledgeIngestResponse;
import com.example.prodsupport.knowledge.dto.KnowledgeStatusResponse;
import com.example.prodsupport.knowledge.model.KnowledgeEvidence;
import com.example.prodsupport.knowledge.model.KnowledgeSource;
import com.example.prodsupport.knowledge.service.KnowledgeIngestionService;
import com.example.prodsupport.knowledge.service.KnowledgeRetrievalService;
import com.example.prodsupport.repository.KnowledgeDocumentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class KnowledgeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KnowledgeIngestionService ingestionService;

    @MockBean
    private KnowledgeRetrievalService retrievalService;

    @MockBean
    private KnowledgeDocumentRepository documentRepository;

    @Test
    @DisplayName("POST /api/knowledge/ingest-all returns 200 with summary counts")
    void testIngestAll() throws Exception {
        KnowledgeBulkIngestResponse bulkResponse = new KnowledgeBulkIngestResponse(
                5, 5, 0, 0, List.of("Ingested 5 documents")
        );
        when(ingestionService.ingestAll()).thenReturn(bulkResponse);

        mockMvc.perform(post("/api/knowledge/ingest-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentsFound", is(5)))
                .andExpect(jsonPath("$.indexed", is(5)))
                .andExpect(jsonPath("$.skipped", is(0)));
    }

    @Test
    @DisplayName("GET /api/knowledge/status returns 200 with status metrics")
    void testGetStatus() throws Exception {
        KnowledgeStatusResponse statusResponse = new KnowledgeStatusResponse(
                "pgvector", true, "ollama", "nomic-embed-text", true, 5
        );
        when(ingestionService.getStatus()).thenReturn(statusResponse);

        mockMvc.perform(get("/api/knowledge/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vectorStore", is("pgvector")))
                .andExpect(jsonPath("$.embeddingModel", is("nomic-embed-text")))
                .andExpect(jsonPath("$.documentCount", is(5)))
                .andExpect(jsonPath("$.databaseAvailable", is(true)));
    }

    @Test
    @DisplayName("POST /api/knowledge/search returns matches and sources")
    void testSearch() throws Exception {
        KnowledgeEvidence evidence = new KnowledgeEvidence(
                "doc-1", "DB Runbook", "RUNBOOK", "runbooks/db.md",
                "Resolution steps for database timeouts", 0.92,
                Map.of("applicationName", "payment-service", "environment", "local"));

        KnowledgeSource source = new KnowledgeSource("DB Runbook", "RUNBOOK", "runbooks/db.md");

        when(retrievalService.retrieve(any(), any(), any(), any(), any()))
                .thenReturn(List.of(evidence));
        when(retrievalService.extractDeduplicatedSources(any()))
                .thenReturn(List.of(source));

        String jsonRequest = """
                {
                    "applicationName": "payment-service",
                    "environment": "local",
                    "query": "database connection pool timeout",
                    "topK": 3
                }
                """;

        mockMvc.perform(post("/api/knowledge/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationName", is("payment-service")))
                .andExpect(jsonPath("$.matches", hasSize(1)))
                .andExpect(jsonPath("$.sources", hasSize(1)))
                .andExpect(jsonPath("$.matches[0].title", is("DB Runbook")));
    }
}
