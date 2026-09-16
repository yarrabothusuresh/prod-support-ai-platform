package com.example.prodsupport.application.controller;

import com.example.prodsupport.application.dto.kafka.KafkaApplicationDiagnosticSummary;
import com.example.prodsupport.application.service.kafka.KafkaDiagnosticService;
import com.example.prodsupport.common.exception.ApplicationNotFoundException;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.infrastructure.kafka.model.KafkaConsumerLagData;
import com.example.prodsupport.infrastructure.repository.RegisteredApplicationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/applications/{id}/diagnostics/kafka")
public class ApplicationKafkaDiagnosticController {

    private final KafkaDiagnosticService kafkaDiagnosticService;
    private final RegisteredApplicationRepository applicationRepository;

    public ApplicationKafkaDiagnosticController(KafkaDiagnosticService kafkaDiagnosticService,
                                                RegisteredApplicationRepository applicationRepository) {
        this.kafkaDiagnosticService = kafkaDiagnosticService;
        this.applicationRepository = applicationRepository;
    }

    @GetMapping
    public ResponseEntity<KafkaApplicationDiagnosticSummary> getKafkaDiagnosticsSummary(@PathVariable Long id) {
        RegisteredApplication app = applicationRepository.findById(id)
                .orElseThrow(() -> new ApplicationNotFoundException(id));

        KafkaApplicationDiagnosticSummary summary = kafkaDiagnosticService.getDiagnosticSummary(app);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/consumer-groups/{group}/lag")
    public ResponseEntity<KafkaConsumerLagData> getConsumerGroupLag(
            @PathVariable Long id,
            @PathVariable String group) {
        RegisteredApplication app = applicationRepository.findById(id)
                .orElseThrow(() -> new ApplicationNotFoundException(id));

        KafkaConsumerLagData lagData = kafkaDiagnosticService.checkConsumerLag(app, group);
        return ResponseEntity.ok(lagData);
    }
}
