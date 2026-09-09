package com.example.prodsupport;

import com.example.prodsupport.ai.client.OllamaSupportAiClient;
import com.example.prodsupport.ai.config.AiProperties;
import com.example.prodsupport.ai.model.ApplicationSupportContext;
import com.example.prodsupport.ai.model.SupportAiResult;
import com.example.prodsupport.ai.prompt.SupportPromptBuilder;
import com.example.prodsupport.common.exception.AiServiceUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OllamaSupportAiClientTest {

    private MockWebServer server;
    private OllamaSupportAiClient aiClient;
    private AiProperties properties;
    private ApplicationSupportContext sampleContext;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        properties = new AiProperties();
        properties.setBaseUrl(server.url("/").toString());
        properties.setModel("llama3:latest");
        properties.setProvider("ollama");

        RestClient pingClient = RestClient.builder().build();
        ObjectMapper objectMapper = new ObjectMapper();
        SupportPromptBuilder promptBuilder = new SupportPromptBuilder();

        // ChatModel is null for unit tests that test parsing, availability, and error handling
        aiClient = new OllamaSupportAiClient((ChatModel) null, promptBuilder, properties, objectMapper, pingClient);

        sampleContext = new ApplicationSupportContext(
                "payment-service",
                "payments",
                "local",
                "Payment Service",
                "http://localhost:8081",
                "UP",
                "UP",
                true,
                true,
                OffsetDateTime.now(),
                List.of()
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    @DisplayName("isAvailable should return true when ping endpoint returns 200 OK")
    void isAvailableShouldReturnTrueWhenPingSucceeds() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"version\":\"0.33.3\"}"));

        assertThat(aiClient.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("isAvailable should return false when ping endpoint returns error or is unreachable")
    void isAvailableShouldReturnFalseWhenPingFails() {
        server.enqueue(new MockResponse().setResponseCode(503));

        assertThat(aiClient.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("analyze should throw AiServiceUnavailableException when provider is not available")
    void analyzeShouldThrowExceptionWhenUnavailable() {
        server.enqueue(new MockResponse().setResponseCode(503));

        assertThatThrownBy(() -> aiClient.analyze(sampleContext, "Is the system UP?"))
                .isInstanceOf(AiServiceUnavailableException.class)
                .hasMessageContaining("Local AI model is currently unavailable");
    }

    @Test
    @DisplayName("parseAiResponse should correctly parse clean structured JSON")
    void shouldParseCleanJson() {
        String json = """
                {
                  "summary": "Service is completely healthy.",
                  "observedFacts": ["Support endpoint reports UP", "Actuator health reports UP"],
                  "possibleCauses": ["No immediate failures observed"],
                  "recommendedChecks": ["Check log volume"],
                  "confidence": "HIGH"
                }
                """;

        SupportAiResult result = aiClient.parseAiResponse(json, sampleContext);

        assertThat(result.summary()).isEqualTo("Service is completely healthy.");
        assertThat(result.observedFacts()).containsExactly("Support endpoint reports UP", "Actuator health reports UP");
        assertThat(result.possibleCauses()).containsExactly("No immediate failures observed");
        assertThat(result.recommendedChecks()).containsExactly("Check log volume");
        assertThat(result.confidence()).isEqualTo("HIGH");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    @DisplayName("parseAiResponse should extract and parse markdown-wrapped JSON")
    void shouldParseMarkdownWrappedJson() {
        String wrappedJson = """
                Here is the analysis:
                ```json
                {
                  "summary": "Service is UP with potential intermittent errors.",
                  "observedFacts": ["Actuator is UP"],
                  "possibleCauses": ["Transient network delay"],
                  "recommendedChecks": ["Review latency metrics"],
                  "confidence": "MEDIUM"
                }
                ```
                Hope this helps!
                """;

        SupportAiResult result = aiClient.parseAiResponse(wrappedJson, sampleContext);

        assertThat(result.summary()).isEqualTo("Service is UP with potential intermittent errors.");
        assertThat(result.observedFacts()).containsExactly("Actuator is UP");
        assertThat(result.confidence()).isEqualTo("MEDIUM");
    }

    @Test
    @DisplayName("parseAiResponse should fall back gracefully on unstructured text without crashing")
    void shouldFallbackGracefullyOnPlainText() {
        String plainText = "The service seems operational, but you should verify logs.";

        SupportAiResult result = aiClient.parseAiResponse(plainText, sampleContext);

        assertThat(result.summary()).contains("The service seems operational");
        assertThat(result.observedFacts()).isNotEmpty();
        assertThat(result.recommendedChecks()).isNotEmpty();
        assertThat(result.confidence()).isEqualTo("LOW");
        assertThat(result.warnings()).isNotEmpty();
        assertThat(result.warnings().get(0)).contains("Model output was unstructured");
    }
}
