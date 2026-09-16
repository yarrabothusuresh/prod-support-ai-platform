package com.example.paymentservice.dto;

import java.math.BigDecimal;

public record PaymentEvent(
        String paymentId,
        BigDecimal amount,
        String currency,
        String createdAt
) {
}
