package com.example.prodsupport.knowledge.dto;

import com.example.prodsupport.knowledge.model.KnowledgeSource;

import java.util.List;

public record SupportKnowledgeChatResponse(
        String applicationName,
        String environment,
        String answer,
        List<KnowledgeSource> sources,
        String confidence,
        List<String> warnings
) {
    public static SupportKnowledgeChatResponse of(String app, String env, String answer,
                                                  List<KnowledgeSource> sources, String confidence,
                                                  List<String> warnings) {
        return new SupportKnowledgeChatResponse(
                app, env, answer,
                sources != null ? sources : List.of(),
                confidence != null ? confidence : "MEDIUM",
                warnings != null ? warnings : List.of()
        );
    }
}
