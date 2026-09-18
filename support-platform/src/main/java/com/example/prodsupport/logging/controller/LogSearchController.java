package com.example.prodsupport.logging.controller;

import com.example.prodsupport.common.exception.ApplicationNotFoundException;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.infrastructure.repository.RegisteredApplicationRepository;
import com.example.prodsupport.logging.dto.LogSearchApiRequest;
import com.example.prodsupport.logging.dto.LogTimelineApiRequest;
import com.example.prodsupport.logging.model.ErrorPatternResult;
import com.example.prodsupport.logging.model.LogSearchResult;
import com.example.prodsupport.logging.model.LogTimelineResult;
import com.example.prodsupport.logging.service.ErrorPatternService;
import com.example.prodsupport.logging.service.LogSearchService;
import com.example.prodsupport.logging.service.LogTimelineService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/applications/{id}/logs")
public class LogSearchController {

    private final RegisteredApplicationRepository applicationRepository;
    private final LogSearchService logSearchService;
    private final ErrorPatternService errorPatternService;
    private final LogTimelineService logTimelineService;

    public LogSearchController(RegisteredApplicationRepository applicationRepository,
                               LogSearchService logSearchService,
                               ErrorPatternService errorPatternService,
                               LogTimelineService logTimelineService) {
        this.applicationRepository = applicationRepository;
        this.logSearchService = logSearchService;
        this.errorPatternService = errorPatternService;
        this.logTimelineService = logTimelineService;
    }

    @PostMapping("/search")
    public ResponseEntity<LogSearchResult> searchLogs(
            @PathVariable Long id,
            @RequestBody(required = false) LogSearchApiRequest request) {
        RegisteredApplication app = applicationRepository.findById(id)
                .orElseThrow(() -> new ApplicationNotFoundException(id));

        LogSearchApiRequest req = request != null ? request : new LogSearchApiRequest(null, null, null, null, null);

        LogSearchResult result = logSearchService.searchLogs(
                app.getApplicationName(),
                app.getEnvironment(),
                req.startTime(),
                req.endTime(),
                req.levels(),
                req.keyword(),
                null,
                req.limit()
        );

        return ResponseEntity.ok(result);
    }

    @GetMapping("/error-patterns")
    public ResponseEntity<ErrorPatternResult> getErrorPatterns(
            @PathVariable Long id,
            @RequestParam(defaultValue = "15") Integer minutes,
            @RequestParam(defaultValue = "10") Integer limit) {
        RegisteredApplication app = applicationRepository.findById(id)
                .orElseThrow(() -> new ApplicationNotFoundException(id));

        ErrorPatternResult result = errorPatternService.summarizeErrors(
                app.getApplicationName(),
                app.getEnvironment(),
                minutes,
                limit
        );

        return ResponseEntity.ok(result);
    }

    @PostMapping("/timeline")
    public ResponseEntity<LogTimelineResult> getTimeline(
            @PathVariable Long id,
            @RequestBody(required = false) LogTimelineApiRequest request) {
        RegisteredApplication app = applicationRepository.findById(id)
                .orElseThrow(() -> new ApplicationNotFoundException(id));

        LogTimelineApiRequest req = request != null ? request : new LogTimelineApiRequest(null, null, null, null);

        LogTimelineResult result = logTimelineService.getTimeline(
                app.getApplicationName(),
                app.getEnvironment(),
                req.startTime(),
                req.endTime(),
                req.correlationId(),
                req.limit()
        );

        return ResponseEntity.ok(result);
    }
}
