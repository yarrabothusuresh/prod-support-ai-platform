package com.example.prodsupport.knowledge.controller;

import com.example.prodsupport.domain.KnowledgeDocument;
import com.example.prodsupport.knowledge.dto.*;
import com.example.prodsupport.knowledge.model.KnowledgeEvidence;
import com.example.prodsupport.knowledge.model.KnowledgeSource;
import com.example.prodsupport.knowledge.service.KnowledgeIngestionService;
import com.example.prodsupport.knowledge.service.KnowledgeRetrievalService;
import com.example.prodsupport.repository.KnowledgeDocumentRepository;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeController.class);

    private final KnowledgeIngestionService ingestionService;
    private final KnowledgeRetrievalService retrievalService;
    private final KnowledgeDocumentRepository documentRepository;

    public KnowledgeController(KnowledgeIngestionService ingestionService,
                               KnowledgeRetrievalService retrievalService,
                               KnowledgeDocumentRepository documentRepository) {
        this.ingestionService = ingestionService;
        this.retrievalService = retrievalService;
        this.documentRepository = documentRepository;
    }

    @PostMapping("/ingest")
    public ResponseEntity<KnowledgeIngestResponse> ingest(@Valid @RequestBody KnowledgeIngestRequest request) {
        log.info("REST request to ingest knowledge document: app='{}', source='{}'", request.applicationName(), request.source());
        KnowledgeIngestResponse response = ingestionService.ingest(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/ingest-all")
    public ResponseEntity<KnowledgeBulkIngestResponse> ingestAll() {
        log.info("REST request to trigger bulk knowledge base ingestion");
        KnowledgeBulkIngestResponse response = ingestionService.ingestAll();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/documents")
    public ResponseEntity<List<KnowledgeDocument>> listDocuments(
            @RequestParam(required = false) String applicationName,
            @RequestParam(required = false) String environment) {
        List<KnowledgeDocument> list;
        if (applicationName != null && environment != null) {
            list = documentRepository.findByApplicationNameAndEnvironment(applicationName, environment);
        } else {
            list = documentRepository.findAll();
        }
        return ResponseEntity.ok(list);
    }

    @GetMapping("/documents/{id}")
    public ResponseEntity<KnowledgeDocument> getDocument(@PathVariable Long id) {
        Optional<KnowledgeDocument> doc = documentRepository.findById(id);
        return doc.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id) {
        log.info("REST request to delete knowledge document id={}", id);
        boolean deleted = ingestionService.deleteDocument(id);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @PostMapping("/search")
    public ResponseEntity<KnowledgeSearchResponse> search(@Valid @RequestBody KnowledgeSearchRequest request) {
        log.info("REST request to search knowledge: app='{}', query='{}', topK={}",
                request.applicationName(), request.query(), request.topK());

        List<KnowledgeEvidence> matches = retrievalService.retrieve(
                request.applicationName(),
                request.environment(),
                request.query(),
                request.documentTypes(),
                request.topK()
        );

        List<KnowledgeSource> sources = retrievalService.extractDeduplicatedSources(matches);

        KnowledgeSearchResponse response = new KnowledgeSearchResponse(
                request.applicationName(),
                request.environment(),
                request.query(),
                matches,
                sources
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/status")
    public ResponseEntity<KnowledgeStatusResponse> getStatus() {
        KnowledgeStatusResponse status = ingestionService.getStatus();
        return ResponseEntity.ok(status);
    }
}
