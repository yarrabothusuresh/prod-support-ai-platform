package com.example.prodsupport.database.service;

import com.example.prodsupport.common.exception.ApplicationNotFoundException;
import com.example.prodsupport.database.entity.ApplicationDatabaseConfigEntity;
import com.example.prodsupport.database.factory.DiagnosticDataSourceFactory;
import com.example.prodsupport.database.model.*;
import com.example.prodsupport.database.provider.DatabaseDiagnosticContext;
import com.example.prodsupport.database.provider.DatabaseDiagnosticProvider;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.infrastructure.repository.RegisteredApplicationRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.*;

@Service
public class ApplicationDatabaseDiagnosticService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationDatabaseDiagnosticService.class);

    private final ApplicationDatabaseConfigService configService;
    private final RegisteredApplicationRepository applicationRepository;
    private final DiagnosticDataSourceFactory dataSourceFactory;
    private final Map<DatabaseType, DatabaseDiagnosticProvider> providers = new EnumMap<>(DatabaseType.class);
    private final RestClient restClient;

    public ApplicationDatabaseDiagnosticService(ApplicationDatabaseConfigService configService,
                                                RegisteredApplicationRepository applicationRepository,
                                                DiagnosticDataSourceFactory dataSourceFactory,
                                                List<DatabaseDiagnosticProvider> providerList,
                                                RestClient.Builder restClientBuilder) {
        this.configService = configService;
        this.applicationRepository = applicationRepository;
        this.dataSourceFactory = dataSourceFactory;
        this.restClient = restClientBuilder.build();

        for (DatabaseDiagnosticProvider p : providerList) {
            this.providers.put(p.databaseType(), p);
        }
    }

    @Transactional(readOnly = true)
    public ApplicationDatabaseDiagnostics runDiagnostics(RegisteredApplication app) {
        Optional<ApplicationDatabaseConfigEntity> configOpt =
                configService.getDatabaseConfigEntity(app.getApplicationName(), app.getEnvironment());

        if (configOpt.isEmpty() || !configOpt.get().isEnabled()) {
            return ApplicationDatabaseDiagnostics.disabled(app.getApplicationName());
        }

        ApplicationDatabaseConfigEntity config = configOpt.get();
        List<String> warnings = new ArrayList<>();

        // 1. Health check via provider
        DatabaseHealthResult health = checkHealth(app);

        // 2. Connection pool check via application endpoint
        ConnectionPoolResult pool = checkConnectionPool(app);
        if ("UNKNOWN".equalsIgnoreCase(pool.status())) {
            warnings.add("Connection pool diagnostic unavailable: " + pool.message());
        }

        // 3. Aggregate activity check via provider
        DatabaseActivityResult activity = checkActivity(app);
        if (activity.warnings() != null && !activity.warnings().isEmpty()) {
            warnings.addAll(activity.warnings());
        }

        return new ApplicationDatabaseDiagnostics(
                true,
                config.getDisplayName(),
                config.getDatabaseType(),
                health,
                pool,
                activity,
                warnings
        );
    }

    @Transactional(readOnly = true)
    public DatabaseHealthResult checkHealth(RegisteredApplication app) {
        Optional<ApplicationDatabaseConfigEntity> configOpt =
                configService.getDatabaseConfigEntity(app.getApplicationName(), app.getEnvironment());

        if (configOpt.isEmpty() || !configOpt.get().isEnabled()) {
            return DatabaseHealthResult.unknown(app.getApplicationName(), DatabaseType.OTHER,
                    "Database diagnostics not configured or disabled for application");
        }

        ApplicationDatabaseConfigEntity config = configOpt.get();
        DatabaseDiagnosticProvider provider = providers.get(config.getDatabaseType());
        if (provider == null) {
            return DatabaseHealthResult.unknown(config.getDisplayName(), config.getDatabaseType(),
                    "No diagnostic provider available for " + config.getDatabaseType());
        }

        try {
            DataSource ds = dataSourceFactory.getOrCreateDataSource(config);
            DatabaseDiagnosticContext context = new DatabaseDiagnosticContext(
                    app.getApplicationName(),
                    app.getEnvironment(),
                    config.getDisplayName(),
                    config.getDatabaseType(),
                    ds,
                    3000,
                    3
            );
            return provider.checkHealth(context);
        } catch (Exception ex) {
            log.warn("Database health check error for {}: {}", config.getDisplayName(), ex.getMessage());
            return DatabaseHealthResult.down(config.getDisplayName(), config.getDatabaseType(),
                    "Failed to create diagnostic datasource: " + ex.getMessage(), 0);
        }
    }

    @Transactional(readOnly = true)
    public DatabaseActivityResult checkActivity(RegisteredApplication app) {
        Optional<ApplicationDatabaseConfigEntity> configOpt =
                configService.getDatabaseConfigEntity(app.getApplicationName(), app.getEnvironment());

        if (configOpt.isEmpty() || !configOpt.get().isEnabled()) {
            return DatabaseActivityResult.unavailable("Database diagnostics not configured or disabled");
        }

        ApplicationDatabaseConfigEntity config = configOpt.get();
        DatabaseDiagnosticProvider provider = providers.get(config.getDatabaseType());
        if (provider == null) {
            return DatabaseActivityResult.unavailable("No provider for " + config.getDatabaseType());
        }

        try {
            DataSource ds = dataSourceFactory.getOrCreateDataSource(config);
            DatabaseDiagnosticContext context = new DatabaseDiagnosticContext(
                    app.getApplicationName(),
                    app.getEnvironment(),
                    config.getDisplayName(),
                    config.getDatabaseType(),
                    ds,
                    3000,
                    3
            );
            return provider.checkActivity(context);
        } catch (Exception ex) {
            log.warn("Database activity check error for {}: {}", config.getDisplayName(), ex.getMessage());
            return DatabaseActivityResult.unavailable("Failed to inspect database activity: " + ex.getMessage());
        }
    }

    public ConnectionPoolResult checkConnectionPool(RegisteredApplication app) {
        String baseUrl = app.getBaseUrl() != null ? app.getBaseUrl().replaceAll("/+$", "") : "";
        if (baseUrl.isBlank()) {
            return ConnectionPoolResult.unavailable("Application baseUrl is missing");
        }

        String poolUrl = baseUrl + "/support/database/pool";
        try {
            StarterPoolPayload payload = restClient.get()
                    .uri(poolUrl)
                    .retrieve()
                    .body(StarterPoolPayload.class);

            if (payload == null) {
                return ConnectionPoolResult.unavailable("Empty response from " + poolUrl);
            }

            return new ConnectionPoolResult(
                    payload.getPoolName() != null ? payload.getPoolName() : "unknown",
                    payload.getActiveConnections(),
                    payload.getIdleConnections(),
                    payload.getTotalConnections(),
                    payload.getMaxPoolSize(),
                    payload.getMinimumIdle(),
                    payload.getThreadsAwaitingConnection(),
                    payload.getUtilizationPercent(),
                    payload.getStatus() != null ? payload.getStatus() : "UNKNOWN",
                    payload.getMessage() != null ? payload.getMessage() : "",
                    "application-endpoint",
                    Instant.now()
            );
        } catch (Exception ex) {
            log.warn("Failed to retrieve pool diagnostics from {}: {}", poolUrl, ex.getMessage());
            return ConnectionPoolResult.unavailable("Failed to query " + poolUrl + ": " + ex.getMessage());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StarterPoolPayload {
        private String poolName;
        private int activeConnections;
        private int idleConnections;
        private int totalConnections;
        private int maxPoolSize;
        private int minimumIdle;
        private int threadsAwaitingConnection;
        private int utilizationPercent;
        private String status;
        private String message;

        public String getPoolName() { return poolName; }
        public void setPoolName(String poolName) { this.poolName = poolName; }
        public int getActiveConnections() { return activeConnections; }
        public void setActiveConnections(int activeConnections) { this.activeConnections = activeConnections; }
        public int getIdleConnections() { return idleConnections; }
        public void setIdleConnections(int idleConnections) { this.idleConnections = idleConnections; }
        public int getTotalConnections() { return totalConnections; }
        public void setTotalConnections(int totalConnections) { this.totalConnections = totalConnections; }
        public int getMaxPoolSize() { return maxPoolSize; }
        public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }
        public int getMinimumIdle() { return minimumIdle; }
        public void setMinimumIdle(int minimumIdle) { this.minimumIdle = minimumIdle; }
        public int getThreadsAwaitingConnection() { return threadsAwaitingConnection; }
        public void setThreadsAwaitingConnection(int threadsAwaitingConnection) { this.threadsAwaitingConnection = threadsAwaitingConnection; }
        public int getUtilizationPercent() { return utilizationPercent; }
        public void setUtilizationPercent(int utilizationPercent) { this.utilizationPercent = utilizationPercent; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}
