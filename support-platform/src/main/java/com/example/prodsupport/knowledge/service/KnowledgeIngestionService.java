package com.example.prodsupport.knowledge.service;

import com.example.prodsupport.ai.config.KnowledgeProperties;
import com.example.prodsupport.domain.DocumentType;
import com.example.prodsupport.domain.KnowledgeDocument;
import com.example.prodsupport.knowledge.dto.KnowledgeBulkIngestResponse;
import com.example.prodsupport.knowledge.dto.KnowledgeIngestRequest;
import com.example.prodsupport.knowledge.dto.KnowledgeIngestResponse;
import com.example.prodsupport.knowledge.dto.KnowledgeStatusResponse;
import com.example.prodsupport.repository.KnowledgeDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

@Service
public class KnowledgeIngestionService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestionService.class);

    private final KnowledgeDocumentRepository documentRepository;
    private final VectorStore vectorStore;
    private final SafeDocumentReader documentReader;
    private final SecretDetector secretDetector;
    private final ContentHasher contentHasher;
    private final DocumentChunker documentChunker;
    private final KnowledgeProperties properties;
    private final EmbeddingModel embeddingModel;

    @Autowired
    public KnowledgeIngestionService(KnowledgeDocumentRepository documentRepository,
                                     VectorStore vectorStore,
                                     SafeDocumentReader documentReader,
                                     SecretDetector secretDetector,
                                     ContentHasher contentHasher,
                                     DocumentChunker documentChunker,
                                     KnowledgeProperties properties,
                                     EmbeddingModel embeddingModel) {
        this.documentRepository = documentRepository;
        this.vectorStore = vectorStore;
        this.documentReader = documentReader;
        this.secretDetector = secretDetector;
        this.contentHasher = contentHasher;
        this.documentChunker = documentChunker;
        this.properties = properties;
        this.embeddingModel = embeddingModel;
    }

    @Transactional
    public KnowledgeIngestResponse ingest(KnowledgeIngestRequest request) {
        long startTime = System.currentTimeMillis();
        String source = request.source();

        try {
            // 1. Read and validate source safely
            String content = documentReader.readDocument(source);

            // 2. Secret and sensitive data detection
            if (secretDetector.containsSecrets(content)) {
                List<String> violations = secretDetector.findSecretMatches(content);
                log.warn("KNOWLEDGE_INGESTION_FAILED reason='secrets detected' app='{}' doc='{}' violations='{}'",
                        request.applicationName(), request.title(), violations);
                throw new SecurityException("Document ingestion rejected: sensitive data/secrets detected (" + violations + ")");
            }

            // 3. Content hash check for idempotency
            String contentHash = contentHasher.computeHash(content);
            Optional<KnowledgeDocument> existingOpt = documentRepository.findByApplicationNameAndEnvironmentAndSource(
                    request.applicationName(), request.environment(), source);

            if (existingOpt.isPresent() && contentHash.equals(existingOpt.get().getContentHash()) && existingOpt.get().isEnabled()) {
                log.info("KNOWLEDGE_INGESTION_SKIPPED app='{}' doc='{}' reason='content hash unchanged'",
                        request.applicationName(), request.title());
                return new KnowledgeIngestResponse(
                        existingOpt.get().getId(),
                        request.applicationName(),
                        request.environment(),
                        request.title(),
                        source,
                        0,
                        "SKIPPED",
                        "Document unchanged (content hash matched)"
                );
            }

            // 4. Persist / update metadata entity
            KnowledgeDocument docEntity = existingOpt.orElseGet(() -> new KnowledgeDocument(
                    request.applicationName(),
                    request.environment(),
                    request.documentType(),
                    request.title(),
                    source,
                    request.version(),
                    request.owner(),
                    contentHash,
                    true
            ));

            docEntity.setTitle(request.title());
            docEntity.setDocumentType(request.documentType());
            docEntity.setVersion(request.version());
            docEntity.setOwner(request.owner());
            docEntity.setContentHash(contentHash);
            docEntity.setEnabled(true);

            docEntity = documentRepository.save(docEntity);

            // 5. Chunk and index into VectorStore
            List<Document> chunks = documentChunker.chunkDocument(
                    docEntity.getId(),
                    request.applicationName(),
                    request.environment(),
                    request.documentType(),
                    request.title(),
                    source,
                    request.version(),
                    request.owner(),
                    content
            );

            if (!chunks.isEmpty()) {
                vectorStore.add(chunks);
            }

            long durationMs = System.currentTimeMillis() - startTime;
            log.info("KNOWLEDGE_INGESTION_SUCCESS app='{}' doc='{}' type='{}' chunks={} durationMs={}",
                    request.applicationName(), request.title(), request.documentType(), chunks.size(), durationMs);

            return new KnowledgeIngestResponse(
                    docEntity.getId(),
                    request.applicationName(),
                    request.environment(),
                    request.title(),
                    source,
                    chunks.size(),
                    "INDEXED",
                    "Document successfully indexed into knowledge base"
            );

        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("KNOWLEDGE_INGESTION_ERROR app='{}' source='{}': {}", request.applicationName(), source, e.getMessage(), e);
            throw new RuntimeException("Failed to ingest document '" + source + "': " + e.getMessage(), e);
        }
    }

    @Transactional
    public KnowledgeBulkIngestResponse ingestAll() {
        Path baseDir = documentReader.getBaseDirectoryPath();
        if (!Files.exists(baseDir) || !Files.isDirectory(baseDir)) {
            return new KnowledgeBulkIngestResponse(0, 0, 0, 0, List.of("Base directory does not exist: " + baseDir));
        }

        int found = 0;
        int indexed = 0;
        int skipped = 0;
        int failed = 0;
        List<String> details = new ArrayList<>();

        try (Stream<Path> stream = Files.walk(baseDir)) {
            List<Path> files = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".md") || p.toString().endsWith(".txt"))
                    .toList();

            found = files.size();

            for (Path file : files) {
                Path relative = baseDir.relativize(file);
                String relativePathStr = "documents/" + relative.toString().replace('\\', '/');

                String appName = "payment-service"; // default application for sample suite
                String environment = "local";
                DocumentType docType = inferDocumentType(relative);
                String title = inferTitle(file);

                KnowledgeIngestRequest request = new KnowledgeIngestRequest(
                        appName,
                        environment,
                        docType,
                        title,
                        relativePathStr,
                        "1.0",
                        "SRE"
                );

                try {
                    KnowledgeIngestResponse response = ingest(request);
                    if ("INDEXED".equals(response.status())) {
                        indexed++;
                        details.add(String.format("INDEXED: %s (%d chunks)", relativePathStr, response.chunksCreated()));
                    } else {
                        skipped++;
                        details.add("SKIPPED: " + relativePathStr + " (" + response.message() + ")");
                    }
                } catch (Exception e) {
                    failed++;
                    details.add("FAILED: " + relativePathStr + " (" + e.getMessage() + ")");
                }
            }

        } catch (IOException e) {
            log.error("Failed to walk documents directory: {}", e.getMessage(), e);
            details.add("Directory walk failure: " + e.getMessage());
        }

        return new KnowledgeBulkIngestResponse(found, indexed, skipped, failed, details);
    }

    @Transactional
    public boolean deleteDocument(Long id) {
        Optional<KnowledgeDocument> docOpt = documentRepository.findById(id);
        if (docOpt.isEmpty()) {
            return false;
        }
        KnowledgeDocument doc = docOpt.get();
        // Delete vector entries if supported
        try {
            // Document chunks were created with ID format: id-chunk-N
            List<String> chunkIds = new ArrayList<>();
            for (int i = 1; i <= 50; i++) {
                chunkIds.add(doc.getId() + "-chunk-" + i);
            }
            vectorStore.delete(chunkIds);
        } catch (Exception e) {
            log.warn("Failed to delete vector chunks for document id {}: {}", id, e.getMessage());
        }

        documentRepository.delete(doc);
        log.info("KNOWLEDGE_DOCUMENT_DELETED id={} app='{}' title='{}'", id, doc.getApplicationName(), doc.getTitle());
        return true;
    }

    public KnowledgeStatusResponse getStatus() {
        boolean dbAvailable = true;
        long docCount = 0;
        try {
            docCount = documentRepository.count();
        } catch (Exception e) {
            dbAvailable = false;
        }

        boolean embeddingAvailable = false;
        try {
            // Check if embedding model is responsive
            float[] sample = embeddingModel.embed("ping");
            embeddingAvailable = (sample != null && sample.length > 0);
        } catch (Exception e) {
            log.debug("Embedding service ping check failed: {}", e.getMessage());
            embeddingAvailable = false;
        }

        return new KnowledgeStatusResponse(
                "pgvector",
                dbAvailable,
                "ollama",
                properties.getEmbeddingModel(),
                embeddingAvailable,
                docCount
        );
    }

    private DocumentType inferDocumentType(Path relativePath) {
        String pathStr = relativePath.toString().toLowerCase();
        if (pathStr.contains("runbook")) return DocumentType.RUNBOOK;
        if (pathStr.contains("architecture")) return DocumentType.ARCHITECTURE;
        if (pathStr.contains("incident")) return DocumentType.INCIDENT;
        if (pathStr.contains("rca")) return DocumentType.RCA;
        if (pathStr.contains("troubleshoot")) return DocumentType.TROUBLESHOOTING;
        if (pathStr.contains("procedure")) return DocumentType.PROCEDURE;
        return DocumentType.OTHER;
    }

    private String inferTitle(Path file) {
        try {
            List<String> lines = Files.readAllLines(file);
            for (String line : lines) {
                if (line.startsWith("# ")) {
                    return line.substring(2).trim();
                }
            }
        } catch (Exception ignored) {
        }
        String fileName = file.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            fileName = fileName.substring(0, dot);
        }
        return fileName.replace('-', ' ').replace('_', ' ');
    }
}
