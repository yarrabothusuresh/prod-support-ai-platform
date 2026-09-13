package com.example.prodsupport.knowledge.service;

import com.example.prodsupport.ai.config.KnowledgeProperties;
import com.example.prodsupport.domain.DocumentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class DocumentChunker {

    private static final Logger log = LoggerFactory.getLogger(DocumentChunker.class);

    private final KnowledgeProperties properties;

    public DocumentChunker(KnowledgeProperties properties) {
        this.properties = properties;
    }

    public List<Document> chunkDocument(com.example.prodsupport.domain.KnowledgeDocument doc, String content) {
        if (doc == null) {
            return Collections.emptyList();
        }
        return chunkDocument(
                doc.getId(),
                doc.getApplicationName(),
                doc.getEnvironment(),
                doc.getDocumentType(),
                doc.getTitle(),
                doc.getSource(),
                doc.getVersion(),
                doc.getOwner(),
                content
        );
    }

    public List<Document> chunkDocument(Long documentId,
                                        String applicationName,
                                        String environment,
                                        DocumentType documentType,
                                        String title,
                                        String source,
                                        String version,
                                        String owner,
                                        String content) {
        if (content == null || content.isBlank()) {
            return Collections.emptyList();
        }

        int chunkSize = properties.getChunkSize() > 0 ? properties.getChunkSize() : 800;
        int chunkOverlap = properties.getChunkOverlap() >= 0 ? properties.getChunkOverlap() : 120;
        if (chunkOverlap >= chunkSize) {
            chunkOverlap = chunkSize / 4;
        }

        List<String> rawChunks = splitText(content, chunkSize, chunkOverlap);
        List<Document> documents = new ArrayList<>();

        for (int i = 0; i < rawChunks.size(); i++) {
            String chunkText = rawChunks.get(i);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("documentId", documentId != null ? documentId.toString() : "");
            metadata.put("applicationName", applicationName != null ? applicationName : "");
            metadata.put("environment", environment != null ? environment : "");
            metadata.put("documentType", documentType != null ? documentType.name() : DocumentType.OTHER.name());
            metadata.put("title", title != null ? title : "");
            metadata.put("source", source != null ? source : "");
            metadata.put("chunkNumber", i + 1);
            metadata.put("version", version != null ? version : "1.0");
            metadata.put("owner", owner != null ? owner : "");

            String chunkId = (documentId != null ? documentId : "doc") + "-chunk-" + (i + 1);
            documents.add(new Document(chunkId, chunkText, metadata));
        }

        log.debug("Chunked document '{}' into {} chunks (size={}, overlap={})",
                title, documents.size(), chunkSize, chunkOverlap);
        return documents;
    }

    private List<String> splitText(String text, int chunkSize, int chunkOverlap) {
        List<String> chunks = new ArrayList<>();
        int textLength = text.length();

        if (textLength <= chunkSize) {
            chunks.add(text.trim());
            return chunks;
        }

        int start = 0;
        while (start < textLength) {
            int end = Math.min(start + chunkSize, textLength);

            // Attempt to break at a natural boundary (newline or period) if near the end
            if (end < textLength) {
                int boundary = findNaturalBoundary(text, start, end);
                if (boundary > start + (chunkSize / 2)) {
                    end = boundary;
                }
            }

            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }

            if (end >= textLength) {
                break;
            }

            start = end - chunkOverlap;
            if (start <= 0 || start >= end) {
                start = end;
            }
        }

        return chunks;
    }

    private int findNaturalBoundary(String text, int start, int end) {
        // Look for double newline (paragraph break) first
        int lastParagraph = text.lastIndexOf("\n\n", end);
        if (lastParagraph > start) {
            return lastParagraph + 2;
        }
        // Then single newline
        int lastNewline = text.lastIndexOf('\n', end);
        if (lastNewline > start) {
            return lastNewline + 1;
        }
        // Then sentence terminator
        int lastPeriod = text.lastIndexOf(". ", end);
        if (lastPeriod > start) {
            return lastPeriod + 2;
        }
        return end;
    }
}
