package com.example.paymentservice.service;

import com.example.paymentservice.dto.PaymentEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class PaymentConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentConsumer.class);
    public static final String LISTENER_ID = "payment-consumer-listener";
    public static final String GROUP_ID = "payment-processing-group";
    public static final String TOPIC = "payment-events";

    private final KafkaListenerEndpointRegistry endpointRegistry;
    private final ObjectMapper objectMapper;
    private final AtomicLong processingDelayMs = new AtomicLong(0);
    private final AtomicBoolean isPaused = new AtomicBoolean(false);
    private final AtomicLong processedCount = new AtomicLong(0);

    public PaymentConsumer(KafkaListenerEndpointRegistry endpointRegistry, ObjectMapper objectMapper) {
        this.endpointRegistry = endpointRegistry;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(id = LISTENER_ID, topics = TOPIC, groupId = GROUP_ID)
    public void consumePaymentEvent(String message) {
        try {
            long delay = processingDelayMs.get();
            if (delay > 0) {
                log.info("Simulating consumer delay of {}ms...", delay);
                Thread.sleep(delay);
            }
            PaymentEvent event = objectMapper.readValue(message, PaymentEvent.class);
            long count = processedCount.incrementAndGet();
            log.info("Processed payment event {} (total processed: {})", event.paymentId(), count);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("Consumer delay interrupted");
        } catch (Exception ex) {
            log.error("Error processing payment event: {}", ex.getMessage(), ex);
        }
    }

    public void pauseConsumer() {
        MessageListenerContainer container = endpointRegistry.getListenerContainer(LISTENER_ID);
        if (container != null) {
            container.pause();
            isPaused.set(true);
            log.info("Kafka consumer '{}' PAUSED", LISTENER_ID);
        } else {
            log.warn("Listener container '{}' not found to pause", LISTENER_ID);
        }
    }

    public void resumeConsumer() {
        MessageListenerContainer container = endpointRegistry.getListenerContainer(LISTENER_ID);
        if (container != null) {
            container.resume();
            isPaused.set(false);
            log.info("Kafka consumer '{}' RESUMED", LISTENER_ID);
        } else {
            log.warn("Listener container '{}' not found to resume", LISTENER_ID);
        }
    }

    public void setProcessingDelay(long ms) {
        processingDelayMs.set(Math.max(0, ms));
        log.info("Kafka consumer processing delay set to {}ms", processingDelayMs.get());
    }

    public long getProcessingDelay() {
        return processingDelayMs.get();
    }

    public boolean isPaused() {
        return isPaused.get();
    }

    public long getProcessedCount() {
        return processedCount.get();
    }
}
