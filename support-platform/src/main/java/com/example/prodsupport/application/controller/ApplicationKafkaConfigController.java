package com.example.prodsupport.application.controller;

import com.example.prodsupport.application.dto.kafka.ApplicationKafkaConfigRequest;
import com.example.prodsupport.application.dto.kafka.ApplicationKafkaConfigResponse;
import com.example.prodsupport.application.service.kafka.ApplicationKafkaConfigService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/applications/{id}/kafka")
public class ApplicationKafkaConfigController {

    private final ApplicationKafkaConfigService kafkaConfigService;

    public ApplicationKafkaConfigController(ApplicationKafkaConfigService kafkaConfigService) {
        this.kafkaConfigService = kafkaConfigService;
    }

    @GetMapping
    public ResponseEntity<ApplicationKafkaConfigResponse> getKafkaConfig(@PathVariable Long id) {
        return kafkaConfigService.getKafkaConfig(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping
    public ResponseEntity<ApplicationKafkaConfigResponse> updateKafkaConfig(
            @PathVariable Long id,
            @Valid @RequestBody ApplicationKafkaConfigRequest request) {
        ApplicationKafkaConfigResponse response = kafkaConfigService.updateKafkaConfig(id, request);
        return ResponseEntity.ok(response);
    }
}
