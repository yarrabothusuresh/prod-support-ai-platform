package com.example.paymentservice.controller;

import com.example.paymentservice.dto.PaymentStatusResponse;
import com.example.prodsupport.starter.service.DependencyHealthService;
import com.example.prodsupport.starter.store.RecentErrorStore;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final ObjectProvider<RecentErrorStore> errorStoreProvider;
    private final ObjectProvider<DependencyHealthService> healthServiceProvider;
    private final ObjectProvider<com.example.paymentservice.service.PaymentProducer> paymentProducerProvider;
    private final ObjectProvider<com.example.paymentservice.repository.PaymentRecordRepository> paymentRepositoryProvider;
    private final ObjectProvider<io.micrometer.tracing.Tracer> tracerProvider;
    private final ObjectProvider<io.micrometer.core.instrument.MeterRegistry> meterRegistryProvider;

    public PaymentController(ObjectProvider<RecentErrorStore> errorStoreProvider,
                             ObjectProvider<DependencyHealthService> healthServiceProvider,
                             ObjectProvider<com.example.paymentservice.service.PaymentProducer> paymentProducerProvider,
                             ObjectProvider<com.example.paymentservice.repository.PaymentRecordRepository> paymentRepositoryProvider,
                             ObjectProvider<io.micrometer.tracing.Tracer> tracerProvider,
                             ObjectProvider<io.micrometer.core.instrument.MeterRegistry> meterRegistryProvider) {
        this.errorStoreProvider = errorStoreProvider;
        this.healthServiceProvider = healthServiceProvider;
        this.paymentProducerProvider = paymentProducerProvider;
        this.paymentRepositoryProvider = paymentRepositoryProvider;
        this.tracerProvider = tracerProvider;
        this.meterRegistryProvider = meterRegistryProvider;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createPayment(@RequestBody(required = false) com.example.paymentservice.dto.CreatePaymentRequest request) {
        io.micrometer.core.instrument.MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        io.micrometer.core.instrument.Timer.Sample timerSample = (registry != null) ? io.micrometer.core.instrument.Timer.start(registry) : null;
        if (registry != null) {
            registry.counter("payment_requests_total", "operation", "create").increment();
        }

        Tracer tracer = tracerProvider.getIfAvailable();

        // 1. Validate payment span
        String paymentId;
        java.math.BigDecimal amount;
        String currency;
        if (tracer != null) {
            io.micrometer.tracing.Span validateSpan = tracer.nextSpan().name("payment.validate").start();
            try (Tracer.SpanInScope ws = tracer.withSpan(validateSpan)) {
                paymentId = (request != null && request.paymentId() != null && !request.paymentId().isBlank()) ?
                        request.paymentId().trim() : "PAY-" + System.currentTimeMillis();
                amount = (request != null && request.amount() != null) ?
                        request.amount() : java.math.BigDecimal.valueOf(1000);
                currency = (request != null && request.currency() != null && !request.currency().isBlank()) ?
                        request.currency().trim() : "INR";
            } finally {
                validateSpan.end();
            }
        } else {
            paymentId = (request != null && request.paymentId() != null && !request.paymentId().isBlank()) ?
                    request.paymentId().trim() : "PAY-" + System.currentTimeMillis();
            amount = (request != null && request.amount() != null) ?
                    request.amount() : java.math.BigDecimal.valueOf(1000);
            currency = (request != null && request.currency() != null && !request.currency().isBlank()) ?
                    request.currency().trim() : "INR";
        }

        // 2. Persist to Database span
        com.example.paymentservice.repository.PaymentRecordRepository repo = paymentRepositoryProvider.getIfAvailable();
        if (repo != null) {
            if (tracer != null) {
                io.micrometer.tracing.Span persistSpan = tracer.nextSpan().name("payment.persist").start();
                try (Tracer.SpanInScope ws = tracer.withSpan(persistSpan)) {
                    repo.save(new com.example.paymentservice.model.PaymentRecord(paymentId, amount, currency, "ACCEPTED"));
                } catch (Exception ex) {
                    persistSpan.error(ex);
                } finally {
                    persistSpan.end();
                }
            } else {
                try {
                    repo.save(new com.example.paymentservice.model.PaymentRecord(paymentId, amount, currency, "ACCEPTED"));
                } catch (Exception ex) {
                    // Keep resilient
                }
            }
        }

        // 3. Publish Kafka Event span
        com.example.paymentservice.dto.PaymentEvent event = new com.example.paymentservice.dto.PaymentEvent(
                paymentId, amount, currency, java.time.Instant.now().toString()
        );

        com.example.paymentservice.service.PaymentProducer producer = paymentProducerProvider.getIfAvailable();
        if (producer != null) {
            if (tracer != null) {
                io.micrometer.tracing.Span pubSpan = tracer.nextSpan().name("payment.publish-event").start();
                try (Tracer.SpanInScope ws = tracer.withSpan(pubSpan)) {
                    producer.publishPaymentEvent(event);
                } catch (Exception ex) {
                    pubSpan.error(ex);
                } finally {
                    pubSpan.end();
                }
            } else {
                producer.publishPaymentEvent(event);
            }
        }

        String activeTraceId = (tracer != null && tracer.currentSpan() != null) ? tracer.currentSpan().context().traceId() : null;
        String activeSpanId = (tracer != null && tracer.currentSpan() != null) ? tracer.currentSpan().context().spanId() : null;

        Map<String, Object> responseBody = new java.util.LinkedHashMap<>();
        responseBody.put("paymentId", paymentId);
        responseBody.put("status", "ACCEPTED");
        responseBody.put("topic", com.example.paymentservice.service.PaymentProducer.TOPIC);
        responseBody.put("amount", amount);
        responseBody.put("currency", currency);
        if (activeTraceId != null) {
            responseBody.put("traceId", activeTraceId);
        }
        if (activeSpanId != null) {
            responseBody.put("spanId", activeSpanId);
        }

        if (registry != null) {
            registry.counter("payment_success_total", "status", "accepted").increment();
            if (timerSample != null) {
                timerSample.stop(registry.timer("payment_processing_duration_seconds", "status", "success"));
            }
        }

        return ResponseEntity.status(org.springframework.http.HttpStatus.ACCEPTED).body(responseBody);
    }

    @GetMapping("/status")
    public ResponseEntity<PaymentStatusResponse> getPaymentStatus() {

        return ResponseEntity.ok(new PaymentStatusResponse("payment-service", "Payment service is running"));
    }

    @PostMapping("/simulate/error")
    public ResponseEntity<Map<String, String>> simulateError(
            @RequestParam(name = "type", defaultValue = "DatabaseTimeoutException") String type,
            @RequestParam(name = "message", defaultValue = "Connection to postgres-db timed out after 3000ms") String message,
            @RequestParam(name = "level", defaultValue = "ERROR") String level) {

        RecentErrorStore store = errorStoreProvider.getIfAvailable();
        if (store != null) {
            store.recordError(level, type, message);
            return ResponseEntity.ok(Map.of(
                    "status", "RECORDED",
                    "type", type,
                    "level", level,
                    "message", message
            ));
        }
        return ResponseEntity.badRequest().body(Map.of("error", "RecentErrorStore bean not available"));
    }

    @PostMapping("/simulate/dependency")
    public ResponseEntity<Map<String, String>> simulateDependencyStatus(
            @RequestParam(name = "dependency", defaultValue = "postgres-db") String dependency,
            @RequestParam(name = "status", defaultValue = "DOWN") String status) {

        DependencyHealthService service = healthServiceProvider.getIfAvailable();
        if (service != null) {
            service.setDependencyOverride(dependency, status);
            return ResponseEntity.ok(Map.of(
                    "status", "UPDATED",
                    "dependency", dependency,
                    "overriddenStatus", status
            ));
        }
        return ResponseEntity.badRequest().body(Map.of("error", "DependencyHealthService bean not available"));
    }

    @DeleteMapping("/simulate/dependency")
    public ResponseEntity<Map<String, String>> clearDependencyOverrides() {
        DependencyHealthService service = healthServiceProvider.getIfAvailable();
        if (service != null) {
            service.clearOverrides();
            return ResponseEntity.ok(Map.of("status", "CLEARED"));
        }
        return ResponseEntity.badRequest().body(Map.of("error", "DependencyHealthService bean not available"));
    }
}
