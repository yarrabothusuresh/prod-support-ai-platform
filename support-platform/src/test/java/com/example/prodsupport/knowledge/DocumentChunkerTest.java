package com.example.prodsupport.knowledge;

import com.example.prodsupport.ai.config.KnowledgeProperties;
import com.example.prodsupport.domain.DocumentType;
import com.example.prodsupport.domain.KnowledgeDocument;
import com.example.prodsupport.knowledge.service.DocumentChunker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentChunkerTest {

    private DocumentChunker chunker;

    @BeforeEach
    void setUp() {
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.setChunkSize(200);
        properties.setChunkOverlap(40);
        chunker = new DocumentChunker(properties);
    }

    @Test
    @DisplayName("Splits long text into multiple chunks with correct metadata")
    void splitsIntoMultipleChunksWithMetadata() {
        KnowledgeDocument entity = new KnowledgeDocument(
                "payment-service",
                "local",
                DocumentType.RUNBOOK,
                "Database Runbook",
                "runbooks/payment-service-database-failure.md",
                "v1.0",
                "SRE Team",
                "abc123hash",
                true
        );

        String longContent = "Line 1: Database failure resolution steps.\n"
                + "Line 2: Check connection pool utilization via actuator prometheus metrics.\n"
                + "Line 3: Identify active long running transactions in pg_stat_activity.\n"
                + "Line 4: Cancel stuck backend PIDs if they exceed query timeout.\n"
                + "Line 5: Increase max connection pool size in application.yml.\n"
                + "Line 6: Perform rolling restart of payment service pods.\n"
                + "Line 7: Verify database health returns to UP state.\n";

        List<Document> chunks = chunker.chunkDocument(entity, longContent);

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.size()).isGreaterThan(1);

        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            assertThat(chunk.getMetadata()).containsEntry("applicationName", "payment-service");
            assertThat(chunk.getMetadata()).containsEntry("environment", "local");
            assertThat(chunk.getMetadata()).containsEntry("documentType", "RUNBOOK");
            assertThat(chunk.getMetadata()).containsEntry("title", "Database Runbook");
            assertThat(chunk.getMetadata()).containsEntry("chunkNumber", i + 1);
            assertThat(chunk.getMetadata()).containsEntry("version", "v1.0");
            assertThat(chunk.getMetadata()).containsEntry("owner", "SRE Team");
            assertThat(chunk.getContent()).isNotBlank();
        }
    }

    @Test
    @DisplayName("Short text produces exactly one chunk")
    void shortTextProducesSingleChunk() {
        KnowledgeDocument entity = new KnowledgeDocument(
                "payment-service",
                "local",
                DocumentType.PROCEDURE,
                "Short Guide",
                "guide.md",
                "1.0",
                "Dev",
                "hash",
                true
        );

        List<Document> chunks = chunker.chunkDocument(entity, "Short procedure details.");
        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().getContent()).isEqualTo("Short procedure details.");
    }
}
