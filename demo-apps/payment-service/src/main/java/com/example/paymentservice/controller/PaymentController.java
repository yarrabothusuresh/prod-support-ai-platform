package com.example.paymentservice.controller;

import com.example.paymentservice.dto.PaymentStatusResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    @GetMapping("/status")
    public ResponseEntity<PaymentStatusResponse> getPaymentStatus() {
        return ResponseEntity.ok(new PaymentStatusResponse("payment-service", "Payment service is running"));
    }
}
