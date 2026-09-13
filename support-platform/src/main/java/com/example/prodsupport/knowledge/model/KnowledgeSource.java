package com.example.prodsupport.knowledge.model;

public record KnowledgeSource(
        String title,
        String documentType,
        String source
) {
    public static KnowledgeSource fromEvidence(KnowledgeEvidence evidence) {
        return new KnowledgeSource(evidence.title(), evidence.documentType(), evidence.source());
    }
}
