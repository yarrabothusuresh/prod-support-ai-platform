package com.example.prodsupport.knowledge;

import com.example.prodsupport.knowledge.dto.SupportKnowledgeChatRequest;
import com.example.prodsupport.knowledge.dto.SupportKnowledgeChatResponse;
import com.example.prodsupport.knowledge.model.KnowledgeSource;
import com.example.prodsupport.knowledge.service.KnowledgeChatService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SupportKnowledgeChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KnowledgeChatService chatService;

    @Test
    @DisplayName("POST /api/support/knowledge-chat returns 200 OK with grounded answer and citations")
    void testKnowledgeChatSuccess() throws Exception {
        KnowledgeSource source = new KnowledgeSource(
                "Payment Runbook", "RUNBOOK", "runbooks/payment.md");

        SupportKnowledgeChatResponse mockResponse = new SupportKnowledgeChatResponse(
                "payment-service",
                "local",
                "According to the Payment Runbook, scale read replicas and verify pool metrics.",
                List.of(source),
                "HIGH",
                List.of()
        );

        when(chatService.chat(any())).thenReturn(mockResponse);

        String json = """
                {
                    "applicationName": "payment-service",
                    "environment": "local",
                    "question": "How do I recover from DB pool exhaustion?"
                }
                """;

        mockMvc.perform(post("/api/support/knowledge-chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationName", is("payment-service")))
                .andExpect(jsonPath("$.answer", is("According to the Payment Runbook, scale read replicas and verify pool metrics.")))
                .andExpect(jsonPath("$.sources", hasSize(1)))
                .andExpect(jsonPath("$.sources[0].title", is("Payment Runbook")));
    }

    @Test
    @DisplayName("POST /api/support/knowledge-chat returns 400 Bad Request when question is missing")
    void testKnowledgeChatValidationFailure() throws Exception {
        String invalidJson = """
                {
                    "applicationName": "payment-service",
                    "environment": "local",
                    "question": ""
                }
                """;

        mockMvc.perform(post("/api/support/knowledge-chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }
}
