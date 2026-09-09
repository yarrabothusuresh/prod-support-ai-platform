package com.example.prodsupport;

import com.example.prodsupport.ai.client.SupportAiClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AiStatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SupportAiClient supportAiClient;

    @Test
    @DisplayName("GET /api/ai/status should return 200 with available=true when AI model is reachable")
    void shouldReturnStatusWhenAvailable() throws Exception {
        when(supportAiClient.getProviderName()).thenReturn("ollama");
        when(supportAiClient.getModelName()).thenReturn("llama3:latest");
        when(supportAiClient.isAvailable()).thenReturn(true);

        mockMvc.perform(get("/api/ai/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider", is("ollama")))
                .andExpect(jsonPath("$.model", is("llama3:latest")))
                .andExpect(jsonPath("$.available", is(true)));
    }

    @Test
    @DisplayName("GET /api/ai/status should return 200 with available=false when AI model is unreachable")
    void shouldReturnStatusWhenUnavailableWithoutThrowing500() throws Exception {
        when(supportAiClient.getProviderName()).thenReturn("ollama");
        when(supportAiClient.getModelName()).thenReturn("llama3:latest");
        when(supportAiClient.isAvailable()).thenReturn(false);

        mockMvc.perform(get("/api/ai/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider", is("ollama")))
                .andExpect(jsonPath("$.model", is("llama3:latest")))
                .andExpect(jsonPath("$.available", is(false)));
    }
}
