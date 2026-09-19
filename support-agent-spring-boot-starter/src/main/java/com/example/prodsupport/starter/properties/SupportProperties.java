package com.example.prodsupport.starter.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "prod-support")
public class SupportProperties {

    /**
     * Whether the production support agent starter is enabled.
     */
    private boolean enabled = false;

    private Application application = new Application();

    private Diagnostics diagnostics = new Diagnostics();

    private TracingConfig tracing = new TracingConfig();

    private List<DependencyConfig> dependencies = new ArrayList<>();

    public TracingConfig getTracing() {
        return tracing;
    }

    public void setTracing(TracingConfig tracing) {
        this.tracing = tracing != null ? tracing : new TracingConfig();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Application getApplication() {
        return application;
    }

    public void setApplication(Application application) {
        this.application = application;
    }

    public Diagnostics getDiagnostics() {
        return diagnostics;
    }

    public void setDiagnostics(Diagnostics diagnostics) {
        this.diagnostics = diagnostics;
    }

    public List<DependencyConfig> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<DependencyConfig> dependencies) {
        this.dependencies = dependencies != null ? dependencies : new ArrayList<>();
    }

    public static class Application {
        private String name;
        private String team;
        private String description;
        private String environment;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getTeam() {
            return team;
        }

        public void setTeam(String team) {
            this.team = team;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getEnvironment() {
            return environment;
        }

        public void setEnvironment(String environment) {
            this.environment = environment;
        }
    }

    public static class Diagnostics {
        private boolean health = true;
        private boolean metrics = true;
        private boolean errors = true;
        private boolean dependencies = true;
        private int maxErrorRetention = 100;
        private DatabaseDiagnostics database = new DatabaseDiagnostics();

        public boolean isHealth() {
            return health;
        }

        public void setHealth(boolean health) {
            this.health = health;
        }

        public boolean isMetrics() {
            return metrics;
        }

        public void setMetrics(boolean metrics) {
            this.metrics = metrics;
        }

        public boolean isErrors() {
            return errors;
        }

        public void setErrors(boolean errors) {
            this.errors = errors;
        }

        public boolean isDependencies() {
            return dependencies;
        }

        public void setDependencies(boolean dependencies) {
            this.dependencies = dependencies;
        }

        public int getMaxErrorRetention() {
            return maxErrorRetention;
        }

        public void setMaxErrorRetention(int maxErrorRetention) {
            this.maxErrorRetention = maxErrorRetention;
        }

        public DatabaseDiagnostics getDatabase() {
            return database;
        }

        public void setDatabase(DatabaseDiagnostics database) {
            this.database = database != null ? database : new DatabaseDiagnostics();
        }
    }

    public static class DatabaseDiagnostics {
        private boolean enabled = true;
        private PoolDiagnostics pool = new PoolDiagnostics();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public PoolDiagnostics getPool() {
            return pool;
        }

        public void setPool(PoolDiagnostics pool) {
            this.pool = pool != null ? pool : new PoolDiagnostics();
        }
    }

    public static class PoolDiagnostics {
        private boolean enabled = true;
        private int warningUtilizationPercent = 80;
        private int criticalUtilizationPercent = 95;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getWarningUtilizationPercent() {
            return warningUtilizationPercent;
        }

        public void setWarningUtilizationPercent(int warningUtilizationPercent) {
            this.warningUtilizationPercent = warningUtilizationPercent;
        }

        public int getCriticalUtilizationPercent() {
            return criticalUtilizationPercent;
        }

        public void setCriticalUtilizationPercent(int criticalUtilizationPercent) {
            this.criticalUtilizationPercent = criticalUtilizationPercent;
        }
    }

    public static class DependencyConfig {
        private String name;
        private String type = "HTTP";
        private String healthUrl;
        private String status = "UP";

        public DependencyConfig() {
        }

        public DependencyConfig(String name, String type, String healthUrl, String status) {
            this.name = name;
            this.type = type;
            this.healthUrl = healthUrl;
            this.status = status != null ? status : "UP";
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getHealthUrl() {
            return healthUrl;
        }

        public void setHealthUrl(String healthUrl) {
            this.healthUrl = healthUrl;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

    public static class TracingConfig {
        private boolean enabled = true;
        private String serviceName;
        private String environment;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getServiceName() {
            return serviceName;
        }

        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }

        public String getEnvironment() {
            return environment;
        }

        public void setEnvironment(String environment) {
            this.environment = environment;
        }
    }
}
