package com.example.prodsupport.application.dto;

import java.util.Collections;
import java.util.List;

public record SupportChatResponse(
        String applicationName,
        String environment,
        String summary,
        List<String> observedFacts,
        List<String> possibleCauses,
        List<String> recommendedChecks,
        String confidence,
        List<String> warnings
) {
    public SupportChatResponse {
        observedFacts = observedFacts == null ? Collections.emptyList() : List.copyOf(observedFacts);
        possibleCauses = possibleCauses == null ? Collections.emptyList() : List.copyOf(possibleCauses);
        recommendedChecks = recommendedChecks == null ? Collections.emptyList() : List.copyOf(recommendedChecks);
        warnings = warnings == null ? Collections.emptyList() : List.copyOf(warnings);
    }
}
