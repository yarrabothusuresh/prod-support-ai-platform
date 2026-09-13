package com.example.prodsupport.knowledge.dto;

import com.example.prodsupport.knowledge.model.KnowledgeEvidence;
import com.example.prodsupport.knowledge.model.KnowledgeSource;

import java.util.List;

public record KnowledgeSearchResponse(
        String applicationName,
        String environment,
        String query,
        List<KnowledgeEvidence> matches,
        List<KnowledgeSource> sources
) {}
