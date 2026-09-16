package com.example.prodsupport.starter.service;

import com.example.prodsupport.starter.model.DatabasePoolDiagnostics;
import com.example.prodsupport.starter.properties.SupportProperties;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import javax.sql.DataSource;
import java.time.Instant;

public class DatabasePoolDiagnosticService {

    private static final Logger log = LoggerFactory.getLogger(DatabasePoolDiagnosticService.class);

    private final SupportProperties properties;
    private final ObjectProvider<DataSource> dataSourceProvider;

    public DatabasePoolDiagnosticService(SupportProperties properties,
                                         ObjectProvider<DataSource> dataSourceProvider) {
        this.properties = properties;
        this.dataSourceProvider = dataSourceProvider;
    }

    public DatabasePoolDiagnostics checkPool() {
        if (properties.getDiagnostics() == null ||
                properties.getDiagnostics().getDatabase() == null ||
                !properties.getDiagnostics().getDatabase().isEnabled() ||
                properties.getDiagnostics().getDatabase().getPool() == null ||
                !properties.getDiagnostics().getDatabase().getPool().isEnabled()) {
            return DatabasePoolDiagnostics.unavailable("Database pool diagnostics are disabled in configuration");
        }

        DataSource dataSource = dataSourceProvider.getIfAvailable();
        if (dataSource == null) {
            return DatabasePoolDiagnostics.unavailable("No DataSource bean available in application context");
        }

        HikariDataSource hikariDataSource = extractHikariDataSource(dataSource);
        if (hikariDataSource == null) {
            return DatabasePoolDiagnostics.unavailable("DataSource is not a HikariDataSource (" + dataSource.getClass().getName() + ")");
        }

        try {
            HikariPoolMXBean poolMxBean = hikariDataSource.getHikariPoolMXBean();
            int active = poolMxBean != null ? poolMxBean.getActiveConnections() : 0;
            int idle = poolMxBean != null ? poolMxBean.getIdleConnections() : 0;
            int total = poolMxBean != null ? poolMxBean.getTotalConnections() : 0;
            int waiting = poolMxBean != null ? poolMxBean.getThreadsAwaitingConnection() : 0;
            int max = hikariDataSource.getMaximumPoolSize();
            int minIdle = hikariDataSource.getMinimumIdle();
            String poolName = hikariDataSource.getPoolName();

            int utilization = max > 0 ? (int) Math.round(((double) active / max) * 100.0) : 0;

            var poolProps = properties.getDiagnostics().getDatabase().getPool();
            int warningThreshold = poolProps.getWarningUtilizationPercent();
            int criticalThreshold = poolProps.getCriticalUtilizationPercent();

            String status = "NORMAL";
            if (utilization >= criticalThreshold || waiting > 0) {
                status = "CRITICAL";
            } else if (utilization >= warningThreshold) {
                status = "WARNING";
            }

            String message = String.format("Hikari pool '%s' is %s (active: %d, max: %d, waiting: %d, utilization: %d%%)",
                    poolName, status, active, max, waiting, utilization);

            return new DatabasePoolDiagnostics(
                    poolName,
                    active,
                    idle,
                    total,
                    max,
                    minIdle,
                    waiting,
                    utilization,
                    status,
                    message,
                    Instant.now()
            );
        } catch (Exception ex) {
            log.warn("Error inspecting Hikari pool: {}", ex.getMessage());
            return DatabasePoolDiagnostics.unavailable("Failed to inspect Hikari pool: " + ex.getMessage());
        }
    }

    private HikariDataSource extractHikariDataSource(DataSource dataSource) {
        if (dataSource instanceof HikariDataSource hikari) {
            return hikari;
        }
        try {
            if (dataSource.isWrapperFor(HikariDataSource.class)) {
                return dataSource.unwrap(HikariDataSource.class);
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
