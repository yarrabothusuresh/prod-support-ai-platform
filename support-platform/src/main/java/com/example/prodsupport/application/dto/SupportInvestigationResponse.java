package com.example.prodsupport.application.dto;

import com.example.prodsupport.knowledge.model.KnowledgeSource;

import java.util.Collections;
import java.util.List;

public record SupportInvestigationResponse(
        String applicationName,
        String environment,
        String summary,
        List<String> observedFacts,
        List<String> likelyCauses,
        List<String> recommendedChecks,
        List<String> knowledgeGuidance,
        List<KnowledgeSource> sources,
        String confidence,
        List<String> toolsUsed,
        int toolExecutionCount,
        List<String> warnings
) {
    public SupportInvestigationResponse {
        observedFacts = observedFacts == null ? Collections.emptyList() : List.copyOf(observedFacts);
        likelyCauses = likelyCauses == null ? Collections.emptyList() : List.copyOf(likelyCauses);
        recommendedChecks = recommendedChecks == null ? Collections.emptyList() : List.copyOf(recommendedChecks);
        knowledgeGuidance = knowledgeGuidance == null ? Collections.emptyList() : List.copyOf(knowledgeGuidance);
        sources = sources == null ? Collections.emptyList() : List.copyOf(sources);
        toolsUsed = toolsUsed == null ? Collections.emptyList() : List.copyOf(toolsUsed);
        warnings = warnings == null ? Collections.emptyList() : List.copyOf(warnings);
    }

    // 10-parameter backward-compatibility constructor for existing Day 4 callers
    public SupportInvestigationResponse(
            String applicationName,
            String environment,
            String summary,
            List<String> observedFacts,
            List<String> likelyCauses,
            List<String> recommendedChecks,
            String confidence,
            List<String> toolsUsed,
            int toolExecutionCount,
            List<String> warnings) {
        this(applicationName, environment, summary, observedFacts, likelyCauses, recommendedChecks,
                Collections.emptyList(), Collections.emptyList(), confidence, toolsUsed, toolExecutionCount, warnings);
    }
}
