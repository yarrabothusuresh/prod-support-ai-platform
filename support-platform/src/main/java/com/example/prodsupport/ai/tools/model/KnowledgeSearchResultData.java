package com.example.prodsupport.ai.tools.model;

import com.example.prodsupport.knowledge.model.KnowledgeEvidence;
import com.example.prodsupport.knowledge.model.KnowledgeSource;

import java.util.List;

public record KnowledgeSearchResultData(
        String applicationName,
        String environment,
        String query,
        int count,
        List<KnowledgeEvidence> matches,
        List<KnowledgeSource> sources
) {}
