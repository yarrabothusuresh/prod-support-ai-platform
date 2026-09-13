package com.example.prodsupport.knowledge.controller;

import com.example.prodsupport.knowledge.dto.SupportKnowledgeChatRequest;
import com.example.prodsupport.knowledge.dto.SupportKnowledgeChatResponse;
import com.example.prodsupport.knowledge.service.KnowledgeChatService;
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
public class SupportKnowledgeChatController {

    private static final Logger log = LoggerFactory.getLogger(SupportKnowledgeChatController.class);

    private final KnowledgeChatService chatService;

    public SupportKnowledgeChatController(KnowledgeChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/knowledge-chat")
    public ResponseEntity<SupportKnowledgeChatResponse> knowledgeChat(
            @Valid @RequestBody SupportKnowledgeChatRequest request) {
        log.info("REST request for knowledge chat: app='{}', question='{}'", request.applicationName(), request.question());
        SupportKnowledgeChatResponse response = chatService.chat(request);
        return ResponseEntity.ok(response);
    }
}
