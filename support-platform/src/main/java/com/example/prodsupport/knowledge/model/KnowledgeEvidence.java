package com.example.prodsupport.knowledge.model;

import java.util.Map;

public record KnowledgeEvidence(
        String documentId,
        String title,
        String documentType,
        String source,
        String content,
        Double score,
        Map<String, Object> metadata
) {
    public static KnowledgeEvidence fromDocument(org.springframework.ai.document.Document doc, Double score) {
        Map<String, Object> meta = doc.getMetadata();
        String docId = meta != null && meta.containsKey("documentId") ? String.valueOf(meta.get("documentId")) : "";
        String title = meta != null && meta.containsKey("title") ? String.valueOf(meta.get("title")) : "Untitled";
        String docType = meta != null && meta.containsKey("documentType") ? String.valueOf(meta.get("documentType")) : "OTHER";
        String source = meta != null && meta.containsKey("source") ? String.valueOf(meta.get("source")) : "";

        return new KnowledgeEvidence(
                docId,
                title,
                docType,
                source,
                doc.getContent(),
                score != null ? score : 0.0,
                meta
        );
    }
}
