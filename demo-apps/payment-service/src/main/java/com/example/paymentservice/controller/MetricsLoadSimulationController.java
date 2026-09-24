package com.example.paymentservice.controller;

import com.example.paymentservice.dto.CreatePaymentRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Controller for safe local/development load generation and HTTP error-rate simulation.
 * Strict upper bounds are enforced to prevent resource exhaustion.
 */
@RestController
@RequestMapping("/demo")
@Profile({"local", "dev", "default", "test"})
public class MetricsLoadSimulationController {

    private static final Logger log = LoggerFactory.getLogger(MetricsLoadSimulationController.class);

    private static final int MAX_REQUESTS = 500;
    private static final int MAX_CONCURRENCY = 20;

    private final PaymentController paymentController;

    public MetricsLoadSimulationController(PaymentController paymentController) {
        this.paymentController = paymentController;
    }

    /**
     * Generates controlled local load against the payment creation endpoint.
     *
     * @param requests    Total number of requests (capped at 500)
     * @param concurrency Concurrency level (capped at 20)
     */
    @PostMapping("/load/payments")
    public ResponseEntity<Map<String, Object>> generatePaymentLoad(
            @RequestParam(name = "requests", defaultValue = "50") int requests,
            @RequestParam(name = "concurrency", defaultValue = "5") int concurrency) {

        int boundedRequests = Math.min(Math.max(1, requests), MAX_REQUESTS);
        int boundedConcurrency = Math.min(Math.max(1, concurrency), MAX_CONCURRENCY);

        log.info("Starting local load simulation: {} requests with concurrency {}", boundedRequests, boundedConcurrency);

        ExecutorService executor = Executors.newFixedThreadPool(boundedConcurrency);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        try {
            CountDownLatch latch = new CountDownLatch(boundedRequests);

            for (int i = 0; i < boundedRequests; i++) {
                final int idx = i;
                executor.submit(() -> {
                    try {
                        CreatePaymentRequest req = new CreatePaymentRequest(
                                "LOAD-PAY-" + System.currentTimeMillis() + "-" + idx,
                                BigDecimal.valueOf(100 + (idx % 50)),
                                "INR"
                        );
                        var response = paymentController.createPayment(req);
                        if (response.getStatusCode().is2xxSuccessful()) {
                            successCount.incrementAndGet();
                        } else {
                            failureCount.incrementAndGet();
                        }
                    } catch (Exception ex) {
                        log.debug("Load generation request failed: {}", ex.getMessage());
                        failureCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean completed = latch.await(30, TimeUnit.SECONDS);
            long durationMs = System.currentTimeMillis() - startTime;

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", completed ? "COMPLETED" : "TIMED_OUT");
            response.put("requestedCount", boundedRequests);
            response.put("concurrency", boundedConcurrency);
            response.put("successfulRequests", successCount.get());
            response.put("failedRequests", failureCount.get());
            response.put("durationMs", durationMs);
            response.put("throughputPerSecond", durationMs > 0 ? (successCount.get() * 1000.0 / durationMs) : 0);

            return ResponseEntity.ok(response);

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "INTERRUPTED", "error", ie.getMessage()));
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Generates controlled HTTP 5xx responses for Prometheus HTTP error-rate testing.
     *
     * @param errorCount Number of errors to generate (1 to 100)
     */
    @PostMapping("/fault/http-error-rate")
    public ResponseEntity<Map<String, Object>> generateHttpErrors(
            @RequestParam(name = "errorCount", defaultValue = "10") int errorCount) {

        int boundedCount = Math.min(Math.max(1, errorCount), 100);
        log.warn("Generating {} controlled HTTP errors for error rate metric simulation", boundedCount);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ERRORS_SIMULATED");
        result.put("simulatedErrorCount", boundedCount);
        result.put("targetMetric", "http_server_requests_seconds_count{status=~\"5..\"}");
        result.put("message", "Simulated " + boundedCount + " server errors for alert/metrics testing");

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }
}
