package com.example.prodsupport.application.controller;

import com.example.prodsupport.application.dto.SupportInvestigationRequest;
import com.example.prodsupport.application.dto.SupportInvestigationResponse;
import com.example.prodsupport.application.service.InvestigationService;
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
public class SupportInvestigationController {

    private static final Logger log = LoggerFactory.getLogger(SupportInvestigationController.class);

    private final InvestigationService investigationService;

    public SupportInvestigationController(InvestigationService investigationService) {
        this.investigationService = investigationService;
    }

    @PostMapping("/investigate")
    public ResponseEntity<SupportInvestigationResponse> investigate(@Valid @RequestBody SupportInvestigationRequest request) {
        log.info("Received support investigation request for application '{}' in environment '{}' (mode={})",
                request.applicationName(), request.environment(), request.mode());

        SupportInvestigationResponse response = investigationService.investigate(request);
        return ResponseEntity.ok(response);
    }
}
