package com.example.prodsupport.application.controller;

import com.example.prodsupport.ai.client.SupportAiClient;
import com.example.prodsupport.application.dto.AiStatusResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiStatusController {

    private static final Logger log = LoggerFactory.getLogger(AiStatusController.class);

    private final SupportAiClient aiClient;

    public AiStatusController(SupportAiClient aiClient) {
        this.aiClient = aiClient;
    }

    @GetMapping("/status")
    public ResponseEntity<AiStatusResponse> getStatus() {
        boolean available = aiClient.isAvailable();
        log.debug("AI status checked: provider={}, model={}, available={}",
                aiClient.getProviderName(), aiClient.getModelName(), available);

        AiStatusResponse response = new AiStatusResponse(
                aiClient.getProviderName(),
                aiClient.getModelName(),
                available
        );
        return ResponseEntity.ok(response);
    }
}
