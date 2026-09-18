package com.example.paymentservice.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/demo/fault/logs")
@Profile({"local", "dev", "default"})
public class LogFaultController {

    private static final Logger log = LoggerFactory.getLogger(LogFaultController.class);

    private static final int MAX_BURST_COUNT = 50;

    @PostMapping("/database-error")
    public ResponseEntity<Map<String, Object>> emitDatabaseError(@RequestParam(defaultValue = "1") int count) {
        int safeCount = Math.clamp(count, 1, MAX_BURST_COUNT);
        String lastCorrelationId = null;

        for (int i = 0; i < safeCount; i++) {
            lastCorrelationId = resolveOrGenerateCorrelationId();
            MDC.put("correlationId", lastCorrelationId);
            MDC.put("component", "database");
            MDC.put("errorType", "DatabaseConnectionException");
            try {
                log.error("Unable to acquire database connection [simulated_fault=true, attempt={}]", i + 1);
            } finally {
                MDC.remove("component");
                MDC.remove("errorType");
            }
        }

        return ResponseEntity.ok(Map.of(
                "status", "EMITTED",
                "eventsEmitted", safeCount,
                "component", "database",
                "errorType", "DatabaseConnectionException",
                "message", "Unable to acquire database connection",
                "lastCorrelationId", lastCorrelationId != null ? lastCorrelationId : "NONE",
                "timestamp", Instant.now().toString(),
                "simulated", true
        ));
    }

    @PostMapping("/http-timeout")
    public ResponseEntity<Map<String, Object>> emitHttpTimeout(@RequestParam(defaultValue = "1") int count) {
        int safeCount = Math.clamp(count, 1, MAX_BURST_COUNT);
        String lastCorrelationId = null;

        for (int i = 0; i < safeCount; i++) {
            lastCorrelationId = resolveOrGenerateCorrelationId();
            MDC.put("correlationId", lastCorrelationId);
            MDC.put("component", "http");
            MDC.put("errorType", "HttpTimeoutException");
            try {
                log.error("Downstream payment gateway request timed out [simulated_fault=true, attempt={}]", i + 1);
            } finally {
                MDC.remove("component");
                MDC.remove("errorType");
            }
        }

        return ResponseEntity.ok(Map.of(
                "status", "EMITTED",
                "eventsEmitted", safeCount,
                "component", "http",
                "errorType", "HttpTimeoutException",
                "message", "Downstream payment gateway request timed out",
                "lastCorrelationId", lastCorrelationId != null ? lastCorrelationId : "NONE",
                "timestamp", Instant.now().toString(),
                "simulated", true
        ));
    }

    @PostMapping("/kafka-error")
    public ResponseEntity<Map<String, Object>> emitKafkaError(@RequestParam(defaultValue = "1") int count) {
        int safeCount = Math.clamp(count, 1, MAX_BURST_COUNT);
        String lastCorrelationId = null;

        for (int i = 0; i < safeCount; i++) {
            lastCorrelationId = resolveOrGenerateCorrelationId();
            MDC.put("correlationId", lastCorrelationId);
            MDC.put("component", "kafka");
            MDC.put("errorType", "KafkaProcessingException");
            try {
                log.error("Payment event processing failed [simulated_fault=true, attempt={}]", i + 1);
            } finally {
                MDC.remove("component");
                MDC.remove("errorType");
            }
        }

        return ResponseEntity.ok(Map.of(
                "status", "EMITTED",
                "eventsEmitted", safeCount,
                "component", "kafka",
                "errorType", "KafkaProcessingException",
                "message", "Payment event processing failed",
                "lastCorrelationId", lastCorrelationId != null ? lastCorrelationId : "NONE",
                "timestamp", Instant.now().toString(),
                "simulated", true
        ));
    }

    private String resolveOrGenerateCorrelationId() {
        String existing = MDC.get("correlationId");
        if (existing != null && !existing.isBlank()) {
            return existing;
        }
        return "CORR-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
