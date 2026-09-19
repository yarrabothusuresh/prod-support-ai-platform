package com.example.notificationservice.dto;

public record NotificationRequest(
        String paymentId,
        String status,
        String recipient
) {}
