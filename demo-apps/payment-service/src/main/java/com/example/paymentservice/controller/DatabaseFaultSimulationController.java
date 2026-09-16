package com.example.paymentservice.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Local/Dev only non-destructive database fault simulation.
 * Simulates pool pressure and slow queries without corrupting database state.
 */
@RestController
@RequestMapping("/demo/fault/database")
@Profile("!prod")
public class DatabaseFaultSimulationController {

    private static final Logger log = LoggerFactory.getLogger(DatabaseFaultSimulationController.class);

    private final DataSource dataSource;
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final List<Connection> heldConnections = Collections.synchronizedList(new ArrayList<>());
    private volatile int artificialSlowDelayMs = 0;

    public DatabaseFaultSimulationController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostMapping("/pool-pressure")
    public ResponseEntity<Map<String, Object>> simulatePoolPressure(
            @RequestParam(name = "connections", defaultValue = "8") int connections,
            @RequestParam(name = "seconds", defaultValue = "20") int seconds) {

        // Enforce safety limits
        int safeConnections = Math.max(1, Math.min(connections, 10)); // max 10
        int safeSeconds = Math.max(1, Math.min(seconds, 30));         // max 30s

        log.info("Simulating pool pressure: holding {} connections for {}s", safeConnections, safeSeconds);

        List<Future<?>> tasks = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(safeConnections);

        for (int i = 0; i < safeConnections; i++) {
            tasks.add(executorService.submit(() -> {
                Connection conn = null;
                try {
                    conn = dataSource.getConnection();
                    heldConnections.add(conn);
                    latch.countDown();
                    Thread.sleep(Duration.ofSeconds(safeSeconds).toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } catch (Exception ex) {
                    log.warn("Failed to acquire connection for pool pressure simulation: {}", ex.getMessage());
                } finally {
                    if (conn != null) {
                        heldConnections.remove(conn);
                        try {
                            conn.close();
                        } catch (Exception ignored) {
                        }
                    }
                }
            }));
        }

        try {
            latch.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        return ResponseEntity.ok(Map.of(
                "status", "PRESSURE_APPLIED",
                "connectionsTarget", safeConnections,
                "currentlyHeld", heldConnections.size(),
                "durationSeconds", safeSeconds,
                "expiresAt", Instant.now().plusSeconds(safeSeconds).toString()
        ));
    }

    @PostMapping("/slow")
    public ResponseEntity<Map<String, Object>> setArtificialSlowDelay(
            @RequestParam(name = "delayMs", defaultValue = "2000") int delayMs) {
        int safeDelay = Math.max(0, Math.min(delayMs, 10000));
        this.artificialSlowDelayMs = safeDelay;
        log.info("Set artificial database slow delay to {}ms", safeDelay);
        return ResponseEntity.ok(Map.of(
                "status", "DELAY_SET",
                "delayMs", safeDelay
        ));
    }

    @PostMapping("/clear")
    public ResponseEntity<Map<String, Object>> clearFaults() {
        log.info("Clearing simulated database faults and releasing all held connections");
        this.artificialSlowDelayMs = 0;
        int releasedCount = 0;
        synchronized (heldConnections) {
            for (Connection c : heldConnections) {
                try {
                    c.close();
                    releasedCount++;
                } catch (Exception ignored) {
                }
            }
            heldConnections.clear();
        }
        return ResponseEntity.ok(Map.of(
                "status", "CLEARED",
                "connectionsReleased", releasedCount
        ));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getFaultStatus() {
        return ResponseEntity.ok(Map.of(
                "currentlyHeldConnections", heldConnections.size(),
                "artificialSlowDelayMs", artificialSlowDelayMs
        ));
    }
}
