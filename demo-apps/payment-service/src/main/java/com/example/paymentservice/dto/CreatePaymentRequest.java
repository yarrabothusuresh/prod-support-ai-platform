package com.example.paymentservice.dto;

import java.math.BigDecimal;

public record CreatePaymentRequest(
        String paymentId,
        BigDecimal amount,
        String currency
) {
}
