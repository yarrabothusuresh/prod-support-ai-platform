package com.example.prodsupport.metrics.service;

import com.example.prodsupport.ai.tools.security.ApplicationAccessValidator;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.metrics.client.MetricsQueryClient;
import com.example.prodsupport.metrics.config.MetricsProperties;
import com.example.prodsupport.metrics.entity.ApplicationMetricsConfigEntity;
import com.example.prodsupport.metrics.model.PrometheusAlertDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PrometheusAlertService {

    private static final Logger log = LoggerFactory.getLogger(PrometheusAlertService.class);

    private final ApplicationAccessValidator accessValidator;
    private final ApplicationMetricsConfigService configService;
    private final MetricsQueryClient queryClient;
    private final MetricsProperties properties;

    public record ActiveAlertsResult(
            String applicationName,
            String environment,
            List<PrometheusAlertDto> activeAlerts,
            List<String> warnings
    ) {}

    public PrometheusAlertService(ApplicationAccessValidator accessValidator,
                                  ApplicationMetricsConfigService configService,
                                  MetricsQueryClient queryClient,
                                  MetricsProperties properties) {
        this.accessValidator = accessValidator;
        this.configService = configService;
        this.queryClient = queryClient;
        this.properties = properties;
    }

    public ActiveAlertsResult getActiveAlerts(String applicationName, String environment) {
        RegisteredApplication app = accessValidator.validateAndGet(applicationName, environment);
        return getActiveAlerts(app);
    }

    public ActiveAlertsResult getActiveAlerts(RegisteredApplication app) {
        List<String> warnings = new ArrayList<>();

        if (!properties.isEnabled() || !configService.isMetricsConfiguredAndEnabled(app)) {
            warnings.add("Metrics collection and alerts are disabled for application '" + app.getApplicationName() + "'");
            return new ActiveAlertsResult(app.getApplicationName(), app.getEnvironment(), List.of(), warnings);
        }

        if (!queryClient.isAvailable()) {
            warnings.add("Prometheus server is currently unreachable. Active alert status cannot be verified.");
            return new ActiveAlertsResult(app.getApplicationName(), app.getEnvironment(), List.of(), warnings);
        }

        ApplicationMetricsConfigEntity config = configService.findEntityByApplication(app)
                .orElseGet(() -> new ApplicationMetricsConfigEntity(app, true, app.getApplicationName(), app.getApplicationName()));

        String targetApp = config.getApplicationLabel();
        String targetJob = config.getPrometheusJob();

        try {
            List<PrometheusAlertDto> allAlerts = queryClient.getAlerts();
            List<PrometheusAlertDto> matchedAlerts = new ArrayList<>();

            for (PrometheusAlertDto alert : allAlerts) {
                // Must be firing
                if (!"FIRING".equalsIgnoreCase(alert.state())) {
                    continue;
                }

                // Filter by registered application / job / environment
                boolean matchApp = targetApp.equalsIgnoreCase(alert.application())
                        || (alert.labels() != null && targetApp.equalsIgnoreCase(alert.labels().get("application")));
                boolean matchJob = targetJob.equalsIgnoreCase(alert.application())
                        || (alert.labels() != null && targetJob.equalsIgnoreCase(alert.labels().get("job")));

                if (matchApp || matchJob) {
                    // Check environment filter if available
                    String alertEnv = alert.environment();
                    if (alertEnv == null || "unknown".equalsIgnoreCase(alertEnv) || app.getEnvironment().equalsIgnoreCase(alertEnv)) {
                        matchedAlerts.add(alert);
                    }
                }
            }

            log.info("Found {} active alerts for application '{}/{}'", matchedAlerts.size(), app.getApplicationName(), app.getEnvironment());
            return new ActiveAlertsResult(app.getApplicationName(), app.getEnvironment(), matchedAlerts, warnings);

        } catch (Exception ex) {
            log.warn("Failed to retrieve active alerts for '{}/{}': {}", app.getApplicationName(), app.getEnvironment(), ex.getMessage());
            warnings.add("Active alerts query error: " + ex.getMessage());
            return new ActiveAlertsResult(app.getApplicationName(), app.getEnvironment(), List.of(), warnings);
        }
    }
}
