package com.example.prodsupport.starter.controller;

import com.example.prodsupport.starter.model.SupportInfoResponse;
import com.example.prodsupport.starter.properties.SupportProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/support")
public class SupportInfoController {

    private static final Logger log = LoggerFactory.getLogger(SupportInfoController.class);

    private final SupportProperties properties;
    private final ObjectProvider<HealthEndpoint> healthEndpointProvider;

    public SupportInfoController(SupportProperties properties, ObjectProvider<HealthEndpoint> healthEndpointProvider) {
        this.properties = properties;
        this.healthEndpointProvider = healthEndpointProvider;
    }

    @GetMapping("/info")
    public ResponseEntity<SupportInfoResponse> getSupportInfo() {
        SupportProperties.Application app = properties.getApplication();

        String status = resolveStatus();

        SupportInfoResponse response = new SupportInfoResponse(
                app != null ? app.getName() : null,
                app != null ? app.getTeam() : null,
                app != null ? app.getEnvironment() : null,
                app != null ? app.getDescription() : null,
                status
        );

        return ResponseEntity.ok(response);
    }

    private String resolveStatus() {
        if (properties.getDiagnostics() != null && properties.getDiagnostics().isHealth()) {
            HealthEndpoint healthEndpoint = healthEndpointProvider.getIfAvailable();
            if (healthEndpoint != null) {
                try {
                    HealthComponent health = healthEndpoint.health();
                    if (health != null && health.getStatus() != null) {
                        return health.getStatus().getCode();
                    }
                } catch (Exception ex) {
                    log.warn("Unable to obtain health status from Actuator HealthEndpoint: {}", ex.getMessage());
                }
            }
        }
        return "UP";
    }
}
