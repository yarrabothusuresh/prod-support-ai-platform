package com.example.prodsupport.tracing.controller;

import com.example.prodsupport.common.exception.ApplicationNotFoundException;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.infrastructure.repository.RegisteredApplicationRepository;
import com.example.prodsupport.tracing.dto.TraceDetailApiResponse;
import com.example.prodsupport.tracing.dto.TraceSearchApiRequest;
import com.example.prodsupport.tracing.dto.TraceSearchApiResponse;
import com.example.prodsupport.tracing.model.TraceDetailResult;
import com.example.prodsupport.tracing.model.TraceSearchResult;
import com.example.prodsupport.tracing.service.TraceSearchService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/applications/{id}/traces")
public class TraceSearchController {

    private final RegisteredApplicationRepository applicationRepository;
    private final TraceSearchService traceSearchService;

    public TraceSearchController(RegisteredApplicationRepository applicationRepository,
                                 TraceSearchService traceSearchService) {
        this.applicationRepository = applicationRepository;
        this.traceSearchService = traceSearchService;
    }

    @PostMapping("/search")
    public ResponseEntity<TraceSearchApiResponse> searchTraces(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) TraceSearchApiRequest request) {

        RegisteredApplication app = applicationRepository.findById(id)
                .orElseThrow(() -> new ApplicationNotFoundException(id));

        int minutes = (request != null && request.minutes() != null) ? request.minutes() : 15;
        int limit = (request != null && request.limit() != null) ? request.limit() : 20;
        boolean errorOnly = (request != null && request.errorOnly() != null) && request.errorOnly();

        TraceSearchResult result = traceSearchService.searchTraces(
                app.getApplicationName(), app.getEnvironment(), minutes, limit, errorOnly
        );

        return ResponseEntity.ok(new TraceSearchApiResponse(
                result.applicationName(),
                result.environment(),
                result.traces(),
                result.warnings()
        ));
    }

    @GetMapping("/{traceId}")
    public ResponseEntity<TraceDetailApiResponse> getTraceDetails(
            @PathVariable Long id,
            @PathVariable String traceId) {

        RegisteredApplication app = applicationRepository.findById(id)
                .orElseThrow(() -> new ApplicationNotFoundException(id));

        TraceDetailResult result = traceSearchService.getTraceDetails(
                app.getApplicationName(), app.getEnvironment(), traceId
        );

        return ResponseEntity.ok(new TraceDetailApiResponse(
                result.traceId(),
                result.durationMs(),
                result.hasError(),
                result.spans(),
                result.warnings()
        ));
    }
}
