package com.example.prodsupport.knowledge.service;

import com.example.prodsupport.knowledge.dto.SupportKnowledgeChatRequest;
import com.example.prodsupport.knowledge.dto.SupportKnowledgeChatResponse;
import com.example.prodsupport.knowledge.model.KnowledgeEvidence;
import com.example.prodsupport.knowledge.model.KnowledgeSource;
import com.example.prodsupport.knowledge.prompt.KnowledgePromptBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class KnowledgeChatService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeChatService.class);

    private final KnowledgeRetrievalService retrievalService;
    private final KnowledgePromptBuilder promptBuilder;
    private final ChatModel chatModel;

    public KnowledgeChatService(KnowledgeRetrievalService retrievalService,
                                KnowledgePromptBuilder promptBuilder,
                                ChatModel chatModel) {
        this.retrievalService = retrievalService;
        this.promptBuilder = promptBuilder;
        this.chatModel = chatModel;
    }

    public SupportKnowledgeChatResponse chat(SupportKnowledgeChatRequest request) {
        log.info("Knowledge chat query for app='{}' env='{}' question='{}'",
                request.applicationName(), request.environment(), request.question());

        List<String> warnings = new ArrayList<>();

        // 1. Retrieve relevant knowledge chunks
        List<KnowledgeEvidence> evidence = retrievalService.retrieve(
                request.applicationName(),
                request.environment(),
                request.question(),
                null, // all document types
                5
        );

        List<KnowledgeSource> sources = retrievalService.extractDeduplicatedSources(evidence);

        if (evidence.isEmpty()) {
            warnings.add("No relevant knowledge base excerpts matched the inquiry.");
        }

        // 2. Build grounded prompt
        String systemPrompt = promptBuilder.buildKnowledgeSystemPrompt();
        String userPrompt = promptBuilder.buildKnowledgeUserPrompt(
                request.applicationName(),
                request.environment(),
                request.question(),
                evidence
        );

        List<Message> messages = List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(userPrompt)
        );

        // 3. Invoke LLM for grounded answer
        String answer;
        String confidence = evidence.isEmpty() ? "LOW" : "HIGH";

        try {
            ChatResponse chatResponse = chatModel.call(new Prompt(messages));
            if (chatResponse != null && chatResponse.getResult() != null && chatResponse.getResult().getOutput() != null) {
                answer = chatResponse.getResult().getOutput().getContent();
            } else {
                answer = "The AI model returned an empty response for this knowledge inquiry.";
                confidence = "LOW";
            }
        } catch (Exception e) {
            log.error("Failed to query chat model for knowledge chat: {}", e.getMessage(), e);
            answer = "AI generation unavailable. Retrieved excerpts: " +
                    evidence.stream().map(e2 -> "[" + e2.title() + "]: " + e2.content()).toList();
            warnings.add("AI model execution failed: " + e.getMessage());
            confidence = "LOW";
        }

        return SupportKnowledgeChatResponse.of(
                request.applicationName(),
                request.environment(),
                answer,
                sources,
                confidence,
                warnings
        );
    }
}
