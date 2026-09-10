package com.example.prodsupport.starter.controller;

import com.example.prodsupport.starter.model.SupportDependenciesResponse;
import com.example.prodsupport.starter.model.SupportDependency;
import com.example.prodsupport.starter.model.SupportError;
import com.example.prodsupport.starter.model.SupportErrorsResponse;
import com.example.prodsupport.starter.properties.SupportProperties;
import com.example.prodsupport.starter.service.DependencyHealthService;
import com.example.prodsupport.starter.store.RecentErrorStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/support")
public class SupportDiagnosticsController {

    private final SupportProperties properties;
    private final RecentErrorStore recentErrorStore;
    private final DependencyHealthService dependencyHealthService;

    public SupportDiagnosticsController(SupportProperties properties,
                                        RecentErrorStore recentErrorStore,
                                        DependencyHealthService dependencyHealthService) {
        this.properties = properties;
        this.recentErrorStore = recentErrorStore;
        this.dependencyHealthService = dependencyHealthService;
    }

    @GetMapping("/errors")
    public ResponseEntity<SupportErrorsResponse> getRecentErrors(
            @RequestParam(name = "limit", defaultValue = "20") int limit) {

        String appName = resolveApplicationName();

        if (properties.getDiagnostics() == null || !properties.getDiagnostics().isErrors()) {
            return ResponseEntity.ok(new SupportErrorsResponse(appName, Collections.emptyList()));
        }

        int maxRetention = properties.getDiagnostics().getMaxErrorRetention() > 0
                ? properties.getDiagnostics().getMaxErrorRetention()
                : 100;
        int safeLimit = limit <= 0 ? 20 : Math.min(limit, maxRetention);

        List<SupportError> errors = recentErrorStore.getRecentErrors(safeLimit);
        return ResponseEntity.ok(new SupportErrorsResponse(appName, errors));
    }

    @GetMapping("/dependencies")
    public ResponseEntity<SupportDependenciesResponse> getDependencies() {
        String appName = resolveApplicationName();

        if (properties.getDiagnostics() == null || !properties.getDiagnostics().isDependencies()) {
            return ResponseEntity.ok(new SupportDependenciesResponse(appName, Collections.emptyList()));
        }

        List<SupportDependency> dependencies = dependencyHealthService.checkDependencies();
        return ResponseEntity.ok(new SupportDependenciesResponse(appName, dependencies));
    }

    private String resolveApplicationName() {
        if (properties.getApplication() != null && properties.getApplication().getName() != null) {
            return properties.getApplication().getName();
        }
        return "Unknown";
    }
}
