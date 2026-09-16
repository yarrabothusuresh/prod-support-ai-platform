package com.example.paymentservice.controller;

import com.example.paymentservice.service.PaymentConsumer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/demo/fault/kafka-consumer")
public class PaymentFaultController {

    private final PaymentConsumer paymentConsumer;

    public PaymentFaultController(PaymentConsumer paymentConsumer) {
        this.paymentConsumer = paymentConsumer;
    }

    @PostMapping("/pause")
    public ResponseEntity<Map<String, Object>> pauseConsumer() {
        paymentConsumer.pauseConsumer();
        return ResponseEntity.ok(Map.of(
                "status", "PAUSED",
                "consumerGroup", PaymentConsumer.GROUP_ID,
                "listenerId", PaymentConsumer.LISTENER_ID
        ));
    }

    @PostMapping("/resume")
    public ResponseEntity<Map<String, Object>> resumeConsumer() {
        paymentConsumer.resumeConsumer();
        return ResponseEntity.ok(Map.of(
                "status", "RESUMED",
                "consumerGroup", PaymentConsumer.GROUP_ID,
                "listenerId", PaymentConsumer.LISTENER_ID
        ));
    }

    @PostMapping("/delay")
    public ResponseEntity<Map<String, Object>> setDelay(@RequestParam(defaultValue = "5000") long milliseconds) {
        long safeDelay = Math.min(60000, Math.max(0, milliseconds));
        paymentConsumer.setProcessingDelay(safeDelay);
        return ResponseEntity.ok(Map.of(
                "status", "DELAY_CONFIGURED",
                "delayMs", safeDelay,
                "consumerGroup", PaymentConsumer.GROUP_ID
        ));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
                "paused", paymentConsumer.isPaused(),
                "delayMs", paymentConsumer.getProcessingDelay(),
                "processedCount", paymentConsumer.getProcessedCount(),
                "consumerGroup", PaymentConsumer.GROUP_ID
        ));
    }
}
