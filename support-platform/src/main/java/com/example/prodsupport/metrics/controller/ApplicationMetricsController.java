package com.example.prodsupport.metrics.controller;

import com.example.prodsupport.common.exception.ApplicationNotFoundException;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.metrics.dto.*;
import com.example.prodsupport.metrics.entity.ApplicationMetricsConfigEntity;
import com.example.prodsupport.metrics.model.PrometheusAlertDto;
import com.example.prodsupport.metrics.service.ApplicationMetricsConfigService;
import com.example.prodsupport.metrics.service.ApplicationMetricsService;
import com.example.prodsupport.metrics.service.PrometheusAlertService;
import com.example.prodsupport.infrastructure.repository.RegisteredApplicationRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/applications/{id}")
public class ApplicationMetricsController {

    private final RegisteredApplicationRepository applicationRepository;
    private final ApplicationMetricsConfigService configService;
    private final ApplicationMetricsService metricsService;
    private final PrometheusAlertService alertService;

    public ApplicationMetricsController(RegisteredApplicationRepository applicationRepository,
                                        ApplicationMetricsConfigService configService,
                                        ApplicationMetricsService metricsService,
                                        PrometheusAlertService alertService) {
        this.applicationRepository = applicationRepository;
        this.configService = configService;
        this.metricsService = metricsService;
        this.alertService = alertService;
    }

    @GetMapping("/metrics")
    public ResponseEntity<ApplicationMetricsConfigDto> getMetricsConfig(@PathVariable Long id) {
        return ResponseEntity.ok(configService.getConfig(id));
    }

    @PutMapping("/metrics")
    public ResponseEntity<ApplicationMetricsConfigDto> updateMetricsConfig(@PathVariable Long id,
                                                                           @RequestBody UpdateMetricsConfigRequest request) {
        return ResponseEntity.ok(configService.updateConfig(id, request));
    }

    @GetMapping("/diagnostics/metrics")
    public ResponseEntity<ApplicationMetricsSummaryDto> getMetricsSummary(
            @PathVariable Long id,
            @RequestParam(name = "windowMinutes", defaultValue = "15") int windowMinutes) {
        RegisteredApplication app = applicationRepository.findById(id)
                .orElseThrow(() -> new ApplicationNotFoundException(id));
        return ResponseEntity.ok(metricsService.getMetricsSummary(app, windowMinutes));
    }

    @PostMapping("/metrics/query")
    public ResponseEntity<Map<String, Object>> executeControlledMetricQuery(
            @PathVariable Long id,
            @Valid @RequestBody MetricQueryRequest request) {

        RegisteredApplication app = applicationRepository.findById(id)
                .orElseThrow(() -> new ApplicationNotFoundException(id));

        int window = (request.minutes() != null && request.minutes() > 0) ? request.minutes() : 15;
        ApplicationMetricsConfigEntity config = configService.findEntityByApplication(app)
                .orElseGet(() -> new ApplicationMetricsConfigEntity(app, true, app.getApplicationName(), app.getApplicationName()));

        String label = config.getApplicationLabel();
        String job = config.getPrometheusJob();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("applicationName", app.getApplicationName());
        result.put("environment", app.getEnvironment());
        result.put("metricType", request.metricType().name());
        result.put("windowMinutes", window);

        switch (request.metricType()) {
            case APPLICATION_AVAILABILITY -> result.put("data", metricsService.getAvailability(job, List.of()));
            case HTTP_REQUEST_RATE, HTTP_ERROR_RATE, HTTP_LATENCY -> result.put("data", metricsService.getHttpMetrics(label, window, List.of()));
            case JVM_HEAP -> result.put("data", metricsService.getJvmMetrics(label, List.of()));
            case CPU -> result.put("data", metricsService.getCpuMetrics(label, List.of()));
            case DATABASE_POOL -> result.put("data", metricsService.getDatabasePoolMetrics(label, List.of()));
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/alerts")
    public ResponseEntity<Map<String, Object>> getActiveAlerts(@PathVariable Long id) {
        RegisteredApplication app = applicationRepository.findById(id)
                .orElseThrow(() -> new ApplicationNotFoundException(id));

        PrometheusAlertService.ActiveAlertsResult alertsResult = alertService.getActiveAlerts(app);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("applicationName", app.getApplicationName());
        response.put("environment", app.getEnvironment());
        response.put("activeAlerts", alertsResult.activeAlerts());
        response.put("warnings", alertsResult.warnings());

        return ResponseEntity.ok(response);
    }
}
