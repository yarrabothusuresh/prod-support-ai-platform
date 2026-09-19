package com.example.notificationservice.dto;

public record NotificationResponse(
        String notificationId,
        String paymentId,
        String status,
        String message
) {}
