package com.example.prodsupport.knowledge.dto;

public record KnowledgeIngestResponse(
        Long documentId,
        String applicationName,
        String environment,
        String title,
        String source,
        int chunksCreated,
        String status,
        String message
) {}
