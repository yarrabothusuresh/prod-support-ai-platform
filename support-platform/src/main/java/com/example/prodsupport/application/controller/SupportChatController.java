package com.example.prodsupport.application.controller;

import com.example.prodsupport.application.dto.SupportChatRequest;
import com.example.prodsupport.application.dto.SupportChatResponse;
import com.example.prodsupport.application.service.SupportChatService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/support")
public class SupportChatController {

    private static final Logger log = LoggerFactory.getLogger(SupportChatController.class);

    private final SupportChatService chatService;

    public SupportChatController(SupportChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/chat")
    public ResponseEntity<SupportChatResponse> chat(@Valid @RequestBody SupportChatRequest request) {
        log.info("Received support chat request for application '{}' in environment '{}'",
                request.applicationName(), request.environment());

        SupportChatResponse response = chatService.chat(request);
        return ResponseEntity.ok(response);
    }
}
