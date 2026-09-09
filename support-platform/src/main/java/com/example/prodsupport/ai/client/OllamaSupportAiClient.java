package com.example.prodsupport.ai.client;

import com.example.prodsupport.ai.config.AiProperties;
import com.example.prodsupport.ai.model.ApplicationSupportContext;
import com.example.prodsupport.ai.model.SupportAiResult;
import com.example.prodsupport.ai.prompt.SupportPromptBuilder;
import com.example.prodsupport.common.exception.AiServiceUnavailableException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class OllamaSupportAiClient implements SupportAiClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaSupportAiClient.class);
    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)\\s*```", Pattern.CASE_INSENSITIVE);

    private final ChatModel chatModel;
    private final SupportPromptBuilder promptBuilder;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final RestClient healthPingClient;

    @Autowired
    public OllamaSupportAiClient(ChatModel chatModel,
                                 SupportPromptBuilder promptBuilder,
                                 AiProperties aiProperties,
                                 ObjectMapper objectMapper,
                                 RestClient.Builder restClientBuilder) {
        this.chatModel = chatModel;
        this.promptBuilder = promptBuilder;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory pingFactory = new SimpleClientHttpRequestFactory();
        pingFactory.setConnectTimeout(Duration.ofSeconds(2));
        pingFactory.setReadTimeout(Duration.ofSeconds(2));
        this.healthPingClient = restClientBuilder
                .requestFactory(pingFactory)
                .build();
    }

    // Constructor for testing
    public OllamaSupportAiClient(ChatModel chatModel,
                                 SupportPromptBuilder promptBuilder,
                                 AiProperties aiProperties,
                                 ObjectMapper objectMapper,
                                 RestClient healthPingClient) {
        this.chatModel = chatModel;
        this.promptBuilder = promptBuilder;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.healthPingClient = healthPingClient;
    }

    @Override
    public SupportAiResult analyze(ApplicationSupportContext context, String userQuestion) {
        log.info("Calling local AI model '{}' for application '{}/{}'",
                aiProperties.getModel(), context.applicationName(), context.environment());

        if (!isAvailable()) {
            log.warn("Ollama AI provider is unavailable at {}", aiProperties.getBaseUrl());
            throw new AiServiceUnavailableException("Local AI model is currently unavailable");
        }

        String systemPrompt = promptBuilder.buildSystemPrompt();
        String userPrompt = promptBuilder.buildUserPrompt(context, userQuestion);

        if (aiProperties.isLogPrompt()) {
            log.debug("AI System Prompt:\n{}", systemPrompt);
            log.debug("AI User Prompt:\n{}", userPrompt);
        }

        List<Message> messages = List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(userPrompt)
        );

        String rawResponseText;
        try {
            Prompt prompt = new Prompt(messages);
            ChatResponse chatResponse = chatModel.call(prompt);

            if (chatResponse == null || chatResponse.getResult() == null || chatResponse.getResult().getOutput() == null) {
                log.warn("Ollama returned empty chat response");
                throw new AiServiceUnavailableException("Local AI model returned an empty response");
            }

            rawResponseText = chatResponse.getResult().getOutput().getContent();
            log.info("AI response received successfully for '{}/{}' (length: {} chars)",
                    context.applicationName(), context.environment(), rawResponseText != null ? rawResponseText.length() : 0);

        } catch (AiServiceUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to invoke local AI model: {}", ex.getMessage(), ex);
            throw new AiServiceUnavailableException("Local AI model is currently unavailable", ex);
        }

        return parseAiResponse(rawResponseText, context);
    }

    @Override
    public boolean isAvailable() {
        try {
            String baseUrl = aiProperties.getBaseUrl().replaceAll("/+$", "");
            String pingUrl = baseUrl + "/api/version";
            var response = healthPingClient.get()
                    .uri(pingUrl)
                    .retrieve()
                    .toBodilessEntity();
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception ex) {
            log.debug("Ollama availability ping failed: {}", ex.getMessage());
            return false;
        }
    }

    @Override
    public String getModelName() {
        return aiProperties.getModel();
    }

    @Override
    public String getProviderName() {
        return aiProperties.getProvider();
    }

    public SupportAiResult parseAiResponse(String rawText, ApplicationSupportContext context) {
        if (rawText == null || rawText.isBlank()) {
            return createFallbackResult("No response generated by AI model.", context, "Empty response received");
        }

        String jsonCandidate = extractJsonString(rawText.trim());

        try {
            StructuredAiPayload payload = objectMapper.readValue(jsonCandidate, StructuredAiPayload.class);
            if (payload != null && payload.getSummary() != null && !payload.getSummary().isBlank()) {
                return new SupportAiResult(
                        payload.getSummary(),
                        payload.getObservedFacts(),
                        payload.getPossibleCauses(),
                        payload.getRecommendedChecks(),
                        payload.getConfidence() != null ? payload.getConfidence().toUpperCase() : "MEDIUM",
                        new ArrayList<>()
                );
            }
        } catch (Exception ex) {
            log.warn("AI response JSON parsing failed: {}. Falling back to text extraction.", ex.getMessage());
        }

        return createFallbackResult(rawText, context, "Model output was unstructured; parsed via fallback logic");
    }

    private String extractJsonString(String text) {
        Matcher matcher = JSON_BLOCK_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        int firstBrace = text.indexOf('{');
        int lastBrace = text.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return text.substring(firstBrace, lastBrace + 1).trim();
        }
        return text;
    }

    private SupportAiResult createFallbackResult(String text, ApplicationSupportContext context, String warningReason) {
        List<String> facts = new ArrayList<>();
        facts.add("Support info status: " + context.supportStatus());
        facts.add("Actuator health status: " + context.actuatorStatus());

        List<String> causes = new ArrayList<>();
        causes.add("Status was evaluated based on live application telemetry");

        List<String> checks = new ArrayList<>();
        checks.add("Check recent application error logs");
        checks.add("Verify connectivity to dependent services and databases");

        List<String> warnings = new ArrayList<>(context.warnings());
        if (warningReason != null) {
            warnings.add(warningReason);
        }

        String summary = text.length() > 300 ? text.substring(0, 300) + "..." : text;

        return new SupportAiResult(
                summary,
                facts,
                causes,
                checks,
                "LOW",
                warnings
        );
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StructuredAiPayload {
        private String summary;
        private List<String> observedFacts = new ArrayList<>();
        private List<String> possibleCauses = new ArrayList<>();
        private List<String> recommendedChecks = new ArrayList<>();
        private String confidence = "MEDIUM";

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }

        public List<String> getObservedFacts() {
            return observedFacts;
        }

        public void setObservedFacts(List<String> observedFacts) {
            this.observedFacts = observedFacts;
        }

        public List<String> getPossibleCauses() {
            return possibleCauses;
        }

        public void setPossibleCauses(List<String> possibleCauses) {
            this.possibleCauses = possibleCauses;
        }

        public List<String> getRecommendedChecks() {
            return recommendedChecks;
        }

        public void setRecommendedChecks(List<String> recommendedChecks) {
            this.recommendedChecks = recommendedChecks;
        }

        public String getConfidence() {
            return confidence;
        }

        public void setConfidence(String confidence) {
            this.confidence = confidence;
        }
    }
}
