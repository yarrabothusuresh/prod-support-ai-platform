package com.example.prodsupport.logging.controller;

import com.example.prodsupport.logging.client.LogSearchClient;
import com.example.prodsupport.logging.config.LoggingProperties;
import com.example.prodsupport.logging.model.LoggingStatusResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/logging")
public class LoggingStatusController {

    private final LogSearchClient logSearchClient;
    private final LoggingProperties properties;

    public LoggingStatusController(LogSearchClient logSearchClient, LoggingProperties properties) {
        this.logSearchClient = logSearchClient;
        this.properties = properties;
    }

    @GetMapping("/status")
    public ResponseEntity<LoggingStatusResponse> getStatus() {
        boolean available = logSearchClient.isAvailable();
        String message = available
                ? "Elasticsearch cluster is reachable and operational"
                : "Centralized log store (Elasticsearch) is currently unavailable";

        LoggingStatusResponse response = new LoggingStatusResponse(
                "elasticsearch",
                available,
                properties.getDefaultIndexPattern(),
                Instant.now().toString(),
                message
        );

        return ResponseEntity.ok(response);
    }
}
