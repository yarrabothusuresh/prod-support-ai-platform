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

    public PaymentController(ObjectProvider<RecentErrorStore> errorStoreProvider,
                             ObjectProvider<DependencyHealthService> healthServiceProvider) {
        this.errorStoreProvider = errorStoreProvider;
        this.healthServiceProvider = healthServiceProvider;
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
