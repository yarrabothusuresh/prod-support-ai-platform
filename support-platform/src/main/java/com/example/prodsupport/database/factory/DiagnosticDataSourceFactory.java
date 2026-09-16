package com.example.prodsupport.database.factory;

import com.example.prodsupport.database.credentials.DatabaseCredentialProvider;
import com.example.prodsupport.database.entity.ApplicationDatabaseConfigEntity;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DiagnosticDataSourceFactory implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticDataSourceFactory.class);

    private final DatabaseCredentialProvider credentialProvider;
    private final Map<Long, HikariDataSource> dataSourceCache = new ConcurrentHashMap<>();

    public DiagnosticDataSourceFactory(DatabaseCredentialProvider credentialProvider) {
        this.credentialProvider = credentialProvider;
    }

    public DataSource getOrCreateDataSource(ApplicationDatabaseConfigEntity config) {
        return dataSourceCache.computeIfAbsent(config.getId(), id -> createDataSource(config));
    }

    private HikariDataSource createDataSource(ApplicationDatabaseConfigEntity config) {
        log.info("Creating diagnostic DataSource for application '{}' (db: {})",
                config.getApplication().getApplicationName(), config.getDisplayName());

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName("DiagPool-" + config.getApplication().getApplicationName());
        hikariConfig.setJdbcUrl(config.getJdbcUrl());
        hikariConfig.setUsername(config.getUsername());

        String password = credentialProvider.resolvePassword(config.getCredentialReference());
        hikariConfig.setPassword(password);

        // Strict timeouts for production safety
        hikariConfig.setConnectionTimeout(3000); // 3 seconds
        hikariConfig.setValidationTimeout(2000); // 2 seconds
        hikariConfig.setMaximumPoolSize(2);      // Tiny pool for diagnostics only
        hikariConfig.setMinimumIdle(0);
        hikariConfig.setIdleTimeout(30000);
        hikariConfig.setMaxLifetime(60000);
        hikariConfig.setReadOnly(true);

        return new HikariDataSource(hikariConfig);
    }

    public void evict(Long configId) {
        HikariDataSource ds = dataSourceCache.remove(configId);
        if (ds != null && !ds.isClosed()) {
            ds.close();
        }
    }

    @Override
    public void destroy() {
        dataSourceCache.values().forEach(ds -> {
            try {
                if (!ds.isClosed()) {
                    ds.close();
                }
            } catch (Exception ignored) {
            }
        });
        dataSourceCache.clear();
    }
}
