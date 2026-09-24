package com.example.prodsupport.metrics.evidence;

import com.example.prodsupport.metrics.dto.ApplicationMetricsSummaryDto;
import com.example.prodsupport.metrics.model.PrometheusAlertDto;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class MetricEvidenceMapper {

    public String buildMetricsSummaryEvidence(ApplicationMetricsSummaryDto summary) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== PROMETHEUS PRODUCTION METRICS SUMMARY ===\n");
        sb.append("Application: ").append(summary.applicationName()).append("\n");
        sb.append("Environment: ").append(summary.environment()).append("\n");
        sb.append("Query Window: Last ").append(summary.windowMinutes()).append(" minutes\n");

        if (summary.availability() != null) {
            sb.append("Availability: ").append(summary.availability().status());
            if (summary.availability().availabilityPercent() != null) {
                sb.append(" (").append(summary.availability().availabilityPercent()).append("%)");
            }
            sb.append("\n");
        }

        if (summary.http() != null) {
            sb.append("HTTP Traffic & Performance:\n");
            if (summary.http().requestRatePerSecond() != null) {
                sb.append("  - Request Rate: ").append(summary.http().requestRatePerSecond()).append(" req/s\n");
            } else {
                sb.append("  - Request Rate: NO DATA\n");
            }
            if (summary.http().errorPercentage() != null) {
                sb.append("  - 5xx Server Error Rate: ").append(summary.http().errorPercentage()).append("%");
                if (summary.http().serverErrorRatePerSecond() != null) {
                    sb.append(" (").append(summary.http().serverErrorRatePerSecond()).append(" err/s)");
                }
                sb.append("\n");
            } else {
                sb.append("  - 5xx Server Error Rate: NO DATA\n");
            }
            if (summary.http().p95LatencyMs() != null) {
                sb.append("  - p95 Latency: ").append(summary.http().p95LatencyMs()).append(" ms\n");
            } else {
                sb.append("  - p95 Latency: NO DATA (Histogram not populated)\n");
            }
            if (summary.http().avgLatencyMs() != null) {
                sb.append("  - Avg Latency: ").append(summary.http().avgLatencyMs()).append(" ms\n");
            }
        }

        if (summary.jvm() != null) {
            sb.append("JVM Runtime Metrics:\n");
            if (summary.jvm().heapUtilizationPercent() != null) {
                sb.append("  - Heap Utilization: ").append(summary.jvm().heapUtilizationPercent()).append("%");
                if (summary.jvm().heapUsedMb() != null && summary.jvm().heapMaxMb() != null) {
                    sb.append(" (").append(summary.jvm().heapUsedMb()).append(" MB / ").append(summary.jvm().heapMaxMb()).append(" MB)");
                }
                sb.append("\n");
            }
            if (summary.jvm().nonHeapUsedMb() != null) {
                sb.append("  - Non-Heap Memory: ").append(summary.jvm().nonHeapUsedMb()).append(" MB\n");
            }
        }

        if (summary.cpu() != null) {
            sb.append("CPU Saturation Metrics:\n");
            if (summary.cpu().processCpuPercent() != null) {
                sb.append("  - Process CPU Usage: ").append(summary.cpu().processCpuPercent()).append("%\n");
            }
            if (summary.cpu().systemCpuPercent() != null) {
                sb.append("  - System CPU Usage: ").append(summary.cpu().systemCpuPercent()).append("%\n");
            }
        }

        if (summary.databasePool() != null) {
            sb.append("Database Connection Pool (HikariCP):\n");
            if (summary.databasePool().utilizationPercent() != null) {
                sb.append("  - Pool Utilization: ").append(summary.databasePool().utilizationPercent()).append("%");
                if (summary.databasePool().activeConnections() != null && summary.databasePool().maxConnections() != null) {
                    sb.append(" (").append(summary.databasePool().activeConnections()).append(" active / ")
                            .append(summary.databasePool().maxConnections()).append(" max)");
                }
                sb.append("\n");
            }
            if (summary.databasePool().pendingConnections() != null) {
                sb.append("  - Pending/Waiting Connection Threads: ").append(summary.databasePool().pendingConnections()).append("\n");
            }
            if (summary.databasePool().idleConnections() != null) {
                sb.append("  - Idle Connections: ").append(summary.databasePool().idleConnections()).append("\n");
            }
        }

        if (summary.warnings() != null && !summary.warnings().isEmpty()) {
            sb.append("Metrics Warnings:\n");
            for (String w : summary.warnings()) {
                sb.append("  - ").append(w).append("\n");
            }
        }

        return sb.toString();
    }

    public String buildAlertsEvidence(String appName, String env, List<PrometheusAlertDto> activeAlerts, List<String> warnings) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== PROMETHEUS ACTIVE ALERTS ===\n");
        sb.append("Application: ").append(appName).append("\n");
        sb.append("Environment: ").append(env).append("\n");

        if (activeAlerts == null || activeAlerts.isEmpty()) {
            sb.append("No active or firing Prometheus alerts recorded for this application.\n");
        } else {
            sb.append("Active Firing Alerts (").append(activeAlerts.size()).append("):\n");
            for (PrometheusAlertDto a : activeAlerts) {
                sb.append("  - [").append(a.state()).append("] ").append(a.name())
                        .append(" (severity=").append(a.severity()).append(", started=").append(a.activeAt()).append(")\n");
                sb.append("    Summary: ").append(a.summary()).append("\n");
                if (a.description() != null && !a.description().equals(a.summary())) {
                    sb.append("    Description: ").append(a.description()).append("\n");
                }
            }
        }

        if (warnings != null && !warnings.isEmpty()) {
            sb.append("Alert Warnings:\n");
            for (String w : warnings) {
                sb.append("  - ").append(w).append("\n");
            }
        }

        return sb.toString();
    }

    public Map<String, Object> toEvidenceItem(String type, String appName, String metric, Object value, String unit, int windowMinutes) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", type);
        item.put("source", "PROMETHEUS");
        item.put("applicationName", appName);
        item.put("metric", metric);
        item.put("value", value);
        item.put("unit", unit);
        item.put("windowMinutes", windowMinutes);
        item.put("collectedAt", Instant.now().toString());
        return item;
    }

    public Map<String, Object> toAlertEvidenceItem(PrometheusAlertDto alert) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "PROMETHEUS_ALERT");
        item.put("source", "PROMETHEUS");
        item.put("alertName", alert.name());
        item.put("state", alert.state());
        item.put("severity", alert.severity());
        item.put("applicationName", alert.application());
        item.put("summary", alert.summary());
        item.put("startedAt", alert.activeAt().toString());
        item.put("collectedAt", Instant.now().toString());
        return item;
    }
}
