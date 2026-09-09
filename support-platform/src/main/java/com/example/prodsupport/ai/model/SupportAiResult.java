package com.example.prodsupport.ai.model;

import java.util.Collections;
import java.util.List;

public record SupportAiResult(
        String summary,
        List<String> observedFacts,
        List<String> possibleCauses,
        List<String> recommendedChecks,
        String confidence,
        List<String> warnings
) {
    public SupportAiResult {
        observedFacts = observedFacts == null ? Collections.emptyList() : List.copyOf(observedFacts);
        possibleCauses = possibleCauses == null ? Collections.emptyList() : List.copyOf(possibleCauses);
        recommendedChecks = recommendedChecks == null ? Collections.emptyList() : List.copyOf(recommendedChecks);
        warnings = warnings == null ? Collections.emptyList() : List.copyOf(warnings);
        if (confidence == null || confidence.isBlank()) {
            confidence = "MEDIUM";
        }
    }
}
