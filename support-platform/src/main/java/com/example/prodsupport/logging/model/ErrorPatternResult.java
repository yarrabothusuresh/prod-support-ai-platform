package com.example.prodsupport.logging.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorPatternResult(
        String applicationName,
        String environment,
        int windowMinutes,
        List<ErrorPatternDto> patterns,
        List<String> warnings
) {
    public ErrorPatternResult {
        if (patterns == null) {
            patterns = List.of();
        }
        if (warnings == null) {
            warnings = List.of();
        }
    }
}
