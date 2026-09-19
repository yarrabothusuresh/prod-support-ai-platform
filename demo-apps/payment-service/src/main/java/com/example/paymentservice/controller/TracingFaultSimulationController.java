package com.example.paymentservice.controller;

import com.example.paymentservice.dto.PaymentEvent;
import com.example.paymentservice.model.PaymentRecord;
import com.example.paymentservice.repository.PaymentRecordRepository;
import com.example.paymentservice.service.PaymentProducer;
import com.example.prodsupport.starter.store.RecentErrorStore;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/demo/fault/tracing")
@Profile({"local", "dev", "default", "test"})
public class TracingFaultSimulationController {

    private static final Logger log = LoggerFactory.getLogger(TracingFaultSimulationController.class);
    private static final long MAX_SLOW_DELAY_MS = 5000;

    private final ObjectProvider<Tracer> tracerProvider;
    private final ObjectProvider<PaymentRecordRepository> repositoryProvider;
    private final ObjectProvider<PaymentProducer> producerProvider;
    private final ObjectProvider<RecentErrorStore> errorStoreProvider;

    public TracingFaultSimulationController(ObjectProvider<Tracer> tracerProvider,
                                            ObjectProvider<PaymentRecordRepository> repositoryProvider,
                                            ObjectProvider<PaymentProducer> producerProvider,
                                            ObjectProvider<RecentErrorStore> errorStoreProvider) {
        this.tracerProvider = tracerProvider;
        this.repositoryProvider = repositoryProvider;
        this.producerProvider = producerProvider;
        this.errorStoreProvider = errorStoreProvider;
    }

    @PostMapping("/slow-payment")
    public ResponseEntity<Map<String, Object>> simulateSlowPayment(
            @RequestParam(name = "delayMs", defaultValue = "1500") long requestedDelayMs) {

        long delayMs = Math.min(Math.max(0, requestedDelayMs), MAX_SLOW_DELAY_MS);
        String paymentId = "PAY-SLOW-" + System.currentTimeMillis();
        Tracer tracer = tracerProvider.getIfAvailable();

        log.info("Simulating slow payment processing with bounded delay {}ms (capped at {}ms)", delayMs, MAX_SLOW_DELAY_MS);

        // 1. Validation span
        if (tracer != null) {
            Span valSpan = tracer.nextSpan().name("payment.validate").start();
            try (Tracer.SpanInScope ws = tracer.withSpan(valSpan)) {
                // validation logic
            } finally {
                valSpan.end();
            }
        }

        // 2. Slow operation span
        if (tracer != null) {
            Span slowSpan = tracer.nextSpan().name("payment.slow-processing").start();
            try (Tracer.SpanInScope ws = tracer.withSpan(slowSpan)) {
                slowSpan.tag("simulation.type", "artificial-delay");
                slowSpan.tag("simulation.delayMs", String.valueOf(delayMs));
                Thread.sleep(delayMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                slowSpan.error(ie);
            } finally {
                slowSpan.end();
            }
        } else {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }

        // 3. Persist
        PaymentRecordRepository repo = repositoryProvider.getIfAvailable();
        if (repo != null) {
            if (tracer != null) {
                Span persistSpan = tracer.nextSpan().name("payment.persist").start();
                try (Tracer.SpanInScope ws = tracer.withSpan(persistSpan)) {
                    repo.save(new PaymentRecord(paymentId, BigDecimal.valueOf(2500), "INR", "ACCEPTED"));
                } catch (Exception ex) {
                    persistSpan.error(ex);
                } finally {
                    persistSpan.end();
                }
            } else {
                try {
                    repo.save(new PaymentRecord(paymentId, BigDecimal.valueOf(2500), "INR", "ACCEPTED"));
                } catch (Exception ignored) {}
            }
        }

        // 4. Publish event
        PaymentProducer producer = producerProvider.getIfAvailable();
        if (producer != null) {
            if (tracer != null) {
                Span pubSpan = tracer.nextSpan().name("payment.publish-event").start();
                try (Tracer.SpanInScope ws = tracer.withSpan(pubSpan)) {
                    producer.publishPaymentEvent(new PaymentEvent(paymentId, BigDecimal.valueOf(2500), "INR", Instant.now().toString()));
                } catch (Exception ex) {
                    pubSpan.error(ex);
                } finally {
                    pubSpan.end();
                }
            } else {
                producer.publishPaymentEvent(new PaymentEvent(paymentId, BigDecimal.valueOf(2500), "INR", Instant.now().toString()));
            }
        }

        String activeTraceId = (tracer != null && tracer.currentSpan() != null) ? tracer.currentSpan().context().traceId() : null;
        String activeSpanId = (tracer != null && tracer.currentSpan() != null) ? tracer.currentSpan().context().spanId() : null;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("paymentId", paymentId);
        body.put("status", "ACCEPTED");
        body.put("simulatedDelayMs", delayMs);
        if (activeTraceId != null) {
            body.put("traceId", activeTraceId);
        }
        if (activeSpanId != null) {
            body.put("spanId", activeSpanId);
        }

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    @PostMapping("/payment-error")
    public ResponseEntity<Map<String, Object>> simulatePaymentError(
            @RequestParam(name = "errorType", defaultValue = "PaymentGatewayException") String errorType,
            @RequestParam(name = "message", defaultValue = "Downstream banking core network connection reset by peer") String errorMessage) {

        String paymentId = "PAY-ERR-" + System.currentTimeMillis();
        Tracer tracer = tracerProvider.getIfAvailable();

        log.warn("Simulating payment error for {}: {} - {}", paymentId, errorType, errorMessage);

        // Record in error store for log & diagnostic correlation
        RecentErrorStore errorStore = errorStoreProvider.getIfAvailable();
        if (errorStore != null) {
            errorStore.recordError("ERROR", errorType, errorMessage);
        }

        if (tracer != null) {
            Span errorSpan = tracer.nextSpan().name("payment.process").start();
            try (Tracer.SpanInScope ws = tracer.withSpan(errorSpan)) {
                errorSpan.tag("error", "true");
                errorSpan.tag("error.type", errorType);
                errorSpan.tag("error.message", errorMessage);
                errorSpan.error(new RuntimeException(errorType + ": " + errorMessage));
            } finally {
                errorSpan.end();
            }
        }

        String activeTraceId = (tracer != null && tracer.currentSpan() != null) ? tracer.currentSpan().context().traceId() : null;
        String activeSpanId = (tracer != null && tracer.currentSpan() != null) ? tracer.currentSpan().context().spanId() : null;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("paymentId", paymentId);
        body.put("status", "FAILED");
        body.put("errorType", errorType);
        body.put("message", errorMessage);
        if (activeTraceId != null) {
            body.put("traceId", activeTraceId);
        }
        if (activeSpanId != null) {
            body.put("spanId", activeSpanId);
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
