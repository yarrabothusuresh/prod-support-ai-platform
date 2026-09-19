package com.example.notificationservice.controller;

import com.example.notificationservice.dto.NotificationRequest;
import com.example.notificationservice.dto.NotificationResponse;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    private final ObjectProvider<Tracer> tracerProvider;

    public NotificationController(ObjectProvider<Tracer> tracerProvider) {
        this.tracerProvider = tracerProvider;
    }

    @PostMapping
    public ResponseEntity<NotificationResponse> sendNotification(@RequestBody(required = false) NotificationRequest request) {
        String paymentId = request != null && request.paymentId() != null ? request.paymentId() : "UNKNOWN";
        String status = request != null && request.status() != null ? request.status() : "COMPLETED";
        String recipient = request != null && request.recipient() != null ? request.recipient() : "customer@example.com";

        Tracer tracer = tracerProvider.getIfAvailable();
        String traceId = (tracer != null && tracer.currentSpan() != null)
                ? tracer.currentSpan().context().traceId()
                : "none";

        log.info("Received notification request for paymentId: {}, status: {}, recipient: {} [traceId={}]",
                paymentId, status, recipient, traceId);

        String notificationId = "NOTIF-" + UUID.randomUUID().toString().substring(0, 8);

        return ResponseEntity.ok(new NotificationResponse(
                notificationId,
                paymentId,
                "SENT",
                "Notification dispatched successfully for payment " + paymentId
        ));
    }
}
