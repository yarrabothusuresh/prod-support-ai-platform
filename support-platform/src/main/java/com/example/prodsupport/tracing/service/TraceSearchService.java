package com.example.prodsupport.tracing.service;

import com.example.prodsupport.ai.tools.security.ApplicationAccessValidator;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.tracing.client.TraceSearchClient;
import com.example.prodsupport.tracing.dto.SpanDetailDto;
import com.example.prodsupport.tracing.dto.TraceSummaryDto;
import com.example.prodsupport.tracing.entity.ApplicationTracingConfigEntity;
import com.example.prodsupport.tracing.model.TraceDetailResult;
import com.example.prodsupport.tracing.model.TraceSearchResult;
import com.example.prodsupport.tracing.repository.ApplicationTracingConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class TraceSearchService {

    private static final Logger log = LoggerFactory.getLogger(TraceSearchService.class);
    private static final Pattern VALID_TRACE_ID_PATTERN = Pattern.compile("^[0-9a-fA-F]{16,32}$");

    private final ApplicationAccessValidator accessValidator;
    private final ApplicationTracingConfigRepository configRepository;
    private final TraceSearchClient traceClient;

    public TraceSearchService(ApplicationAccessValidator accessValidator,
                              ApplicationTracingConfigRepository configRepository,
                              TraceSearchClient traceClient) {
        this.accessValidator = accessValidator;
        this.configRepository = configRepository;
        this.traceClient = traceClient;
    }

    public TraceSearchResult searchTraces(String applicationName, String environment, int minutes, int limit, boolean errorOnly) {
        RegisteredApplication app = accessValidator.validateAndGet(applicationName, environment);

        ApplicationTracingConfigEntity config = configRepository.findByApplicationNameAndEnvironment(
                app.getApplicationName(), app.getEnvironment()
        ).orElse(null);

        if (config != null && !config.isTracingEnabled()) {
            return new TraceSearchResult(app.getApplicationName(), app.getEnvironment(), List.of(),
                    List.of("Distributed tracing is disabled for application '" + app.getApplicationName() + "'"));
        }

        String telemetryServiceName = (config != null && config.getTracingServiceName() != null)
                ? config.getTracingServiceName()
                : app.getApplicationName();

        log.info("Searching traces for application '{}/{}' using telemetry service '{}' (lookback={}m, limit={}, errorOnly={})",
                app.getApplicationName(), app.getEnvironment(), telemetryServiceName, minutes, limit, errorOnly);

        return traceClient.searchTraces(telemetryServiceName, app.getEnvironment(), minutes, limit, errorOnly);
    }

    public TraceDetailResult getTraceDetails(String applicationName, String environment, String traceId) {
        if (traceId == null || !VALID_TRACE_ID_PATTERN.matcher(traceId.trim()).matches()) {
            throw new IllegalArgumentException("Invalid traceId format: '" + traceId + "'. Must be 16 to 32 hex characters.");
        }

        RegisteredApplication app = accessValidator.validateAndGet(applicationName, environment);

        ApplicationTracingConfigEntity config = configRepository.findByApplicationNameAndEnvironment(
                app.getApplicationName(), app.getEnvironment()
        ).orElse(null);

        if (config != null && !config.isTracingEnabled()) {
            return new TraceDetailResult(traceId.trim(), 0, false, List.of(), Set.of(),
                    List.of("Distributed tracing is disabled for application '" + app.getApplicationName() + "'"));
        }

        String telemetryServiceName = (config != null && config.getTracingServiceName() != null)
                ? config.getTracingServiceName()
                : app.getApplicationName();

        Optional<TraceDetailResult> optResult = traceClient.getTraceById(traceId.trim());
        if (optResult.isEmpty()) {
            return new TraceDetailResult(traceId.trim(), 0, false, List.of(), Set.of(),
                    List.of("Trace with ID '" + traceId + "' was not found in tracing storage."));
        }

        TraceDetailResult rawResult = optResult.get();

        // Enforce application scope: verify that the application's telemetry service participates in the trace
        boolean applicationParticipates = rawResult.participatingServices().contains(telemetryServiceName);
        if (!applicationParticipates && !rawResult.spans().isEmpty()) {
            log.warn("Access violation attempt: Trace '{}' does not belong to authorized service '{}'",
                    traceId, telemetryServiceName);
            return new TraceDetailResult(traceId.trim(), 0, false, List.of(), Set.of(),
                    List.of("Trace '" + traceId + "' does not belong to the authorized investigation scope of application '" + applicationName + "'"));
        }

        return rawResult;
    }

    public boolean isTracingConfiguredAndEnabled(RegisteredApplication app) {
        if (app == null) return false;
        return configRepository.findByApplicationNameAndEnvironment(app.getApplicationName(), app.getEnvironment())
                .map(ApplicationTracingConfigEntity::isTracingEnabled)
                .orElse(true);
    }
}
