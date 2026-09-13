package com.example.prodsupport.knowledge.dto;

public record KnowledgeStatusResponse(
        String vectorStore,
        boolean databaseAvailable,
        String embeddingProvider,
        String embeddingModel,
        boolean embeddingAvailable,
        long documentCount
) {}
