package com.example.prodsupport.tracing.controller;

import com.example.prodsupport.tracing.client.TraceSearchClient;
import com.example.prodsupport.tracing.dto.TracingStatusResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/tracing")
public class TracingStatusController {

    private final TraceSearchClient traceClient;

    public TracingStatusController(TraceSearchClient traceClient) {
        this.traceClient = traceClient;
    }

    @GetMapping("/status")
    public ResponseEntity<TracingStatusResponse> getTracingStatus() {
        boolean available = traceClient.isAvailable();
        return ResponseEntity.ok(new TracingStatusResponse(
                traceClient.getProviderName(),
                available,
                Instant.now().toString(),
                traceClient.getEndpointUrl()
        ));
    }
}
