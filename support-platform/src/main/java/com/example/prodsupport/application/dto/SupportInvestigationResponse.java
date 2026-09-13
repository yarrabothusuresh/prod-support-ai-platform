package com.example.prodsupport.application.dto;

import java.util.Collections;
import java.util.List;

public record SupportInvestigationResponse(
        String applicationName,
        String environment,
        String summary,
        List<String> observedFacts,
        List<String> likelyCauses,
        List<String> recommendedChecks,
        String confidence,
        List<String> toolsUsed,
        int toolExecutionCount,
        List<String> warnings
) {
    public SupportInvestigationResponse {
        observedFacts = observedFacts == null ? Collections.emptyList() : List.copyOf(observedFacts);
        likelyCauses = likelyCauses == null ? Collections.emptyList() : List.copyOf(likelyCauses);
        recommendedChecks = recommendedChecks == null ? Collections.emptyList() : List.copyOf(recommendedChecks);
        toolsUsed = toolsUsed == null ? Collections.emptyList() : List.copyOf(toolsUsed);
        warnings = warnings == null ? Collections.emptyList() : List.copyOf(warnings);
    }
}
