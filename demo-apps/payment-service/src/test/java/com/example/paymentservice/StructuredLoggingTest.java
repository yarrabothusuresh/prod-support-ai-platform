package com.example.paymentservice;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.paymentservice.controller.LogFaultController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StructuredLoggingTest {

    private ListAppender<ILoggingEvent> listAppender;
    private Logger logger;
    private LogFaultController controller;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(LogFaultController.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
        controller = new LogFaultController();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(listAppender);
        listAppender.stop();
        MDC.clear();
    }

    @Test
    @DisplayName("Should emit structured database-error log with ERROR level and component/errorType metadata")
    void testDatabaseErrorFault() {
        ResponseEntity<Map<String, Object>> response = controller.emitDatabaseError(1);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("component")).isEqualTo("database");
        assertThat(response.getBody().get("errorType")).isEqualTo("DatabaseConnectionException");

        assertThat(listAppender.list).isNotEmpty();
        ILoggingEvent event = listAppender.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getMessage()).contains("Unable to acquire database connection");
        assertThat(event.getMDCPropertyMap()).containsKey("correlationId");
    }

    @Test
    @DisplayName("Should emit structured http-timeout log with ERROR level")
    void testHttpTimeoutFault() {
        ResponseEntity<Map<String, Object>> response = controller.emitHttpTimeout(1);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("component")).isEqualTo("http");
        assertThat(response.getBody().get("errorType")).isEqualTo("HttpTimeoutException");

        assertThat(listAppender.list).isNotEmpty();
        ILoggingEvent event = listAppender.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getMessage()).contains("Downstream payment gateway request timed out");
    }

    @Test
    @DisplayName("Should emit structured kafka-error log with ERROR level")
    void testKafkaErrorFault() {
        ResponseEntity<Map<String, Object>> response = controller.emitKafkaError(1);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("component")).isEqualTo("kafka");
        assertThat(response.getBody().get("errorType")).isEqualTo("KafkaProcessingException");

        assertThat(listAppender.list).isNotEmpty();
        ILoggingEvent event = listAppender.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getMessage()).contains("Payment event processing failed");
    }
}
