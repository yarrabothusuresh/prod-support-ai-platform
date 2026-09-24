package com.example.prodsupport.metrics.controller;

import com.example.prodsupport.metrics.client.MetricsQueryClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/metrics")
public class MetricsStatusController {

    private final MetricsQueryClient metricsQueryClient;

    public MetricsStatusController(MetricsQueryClient metricsQueryClient) {
        this.metricsQueryClient = metricsQueryClient;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getMetricsStatus() {
        boolean available = metricsQueryClient.isAvailable();

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", true);
        status.put("provider", metricsQueryClient.getProviderName());
        status.put("available", available);
        status.put("endpointUrl", metricsQueryClient.getEndpointUrl());
        status.put("lastCheckedAt", Instant.now().toString());

        return ResponseEntity.ok(status);
    }
}
