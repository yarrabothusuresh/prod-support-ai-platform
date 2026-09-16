package com.example.paymentservice.controller;

import com.example.paymentservice.dto.PaymentStatusResponse;
import com.example.prodsupport.starter.service.DependencyHealthService;
import com.example.prodsupport.starter.store.RecentErrorStore;
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

    public PaymentController(ObjectProvider<RecentErrorStore> errorStoreProvider,
                             ObjectProvider<DependencyHealthService> healthServiceProvider,
                             ObjectProvider<com.example.paymentservice.service.PaymentProducer> paymentProducerProvider,
                             ObjectProvider<com.example.paymentservice.repository.PaymentRecordRepository> paymentRepositoryProvider) {
        this.errorStoreProvider = errorStoreProvider;
        this.healthServiceProvider = healthServiceProvider;
        this.paymentProducerProvider = paymentProducerProvider;
        this.paymentRepositoryProvider = paymentRepositoryProvider;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createPayment(@RequestBody(required = false) com.example.paymentservice.dto.CreatePaymentRequest request) {
        String paymentId = (request != null && request.paymentId() != null && !request.paymentId().isBlank()) ?
                request.paymentId().trim() : "PAY-" + System.currentTimeMillis();
        java.math.BigDecimal amount = (request != null && request.amount() != null) ?
                request.amount() : java.math.BigDecimal.valueOf(1000);
        String currency = (request != null && request.currency() != null && !request.currency().isBlank()) ?
                request.currency().trim() : "INR";

        // Save to Database
        com.example.paymentservice.repository.PaymentRecordRepository repo = paymentRepositoryProvider.getIfAvailable();
        if (repo != null) {
            try {
                repo.save(new com.example.paymentservice.model.PaymentRecord(paymentId, amount, currency, "ACCEPTED"));
            } catch (Exception ex) {
                // Keep resilient
            }
        }

        com.example.paymentservice.dto.PaymentEvent event = new com.example.paymentservice.dto.PaymentEvent(
                paymentId, amount, currency, java.time.Instant.now().toString()
        );

        com.example.paymentservice.service.PaymentProducer producer = paymentProducerProvider.getIfAvailable();
        if (producer != null) {
            producer.publishPaymentEvent(event);
        }

        return ResponseEntity.status(org.springframework.http.HttpStatus.ACCEPTED).body(Map.of(
                "paymentId", paymentId,
                "status", "ACCEPTED",
                "topic", com.example.paymentservice.service.PaymentProducer.TOPIC,
                "amount", amount,
                "currency", currency
        ));
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
