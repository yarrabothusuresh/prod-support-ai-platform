package com.example.prodsupport.application.controller;

import com.example.prodsupport.application.dto.RegisterApplicationRequest;
import com.example.prodsupport.application.dto.RegisteredApplicationResponse;
import com.example.prodsupport.application.dto.TestConnectionResponse;
import com.example.prodsupport.application.service.ApplicationRegistryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationRegistryService registryService;

    public ApplicationController(ApplicationRegistryService registryService) {
        this.registryService = registryService;
    }

    @PostMapping
    public ResponseEntity<RegisteredApplicationResponse> registerApplication(
            @Valid @RequestBody RegisterApplicationRequest request) {
        RegisteredApplicationResponse response = registryService.registerApplication(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<RegisteredApplicationResponse>> getAllApplications() {
        return ResponseEntity.ok(registryService.getAllApplications());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RegisteredApplicationResponse> getApplicationById(@PathVariable Long id) {
        return ResponseEntity.ok(registryService.getApplicationById(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteApplication(@PathVariable Long id) {
        registryService.deleteApplication(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/test-connection")
    public ResponseEntity<TestConnectionResponse> testConnection(@PathVariable Long id) {
        TestConnectionResponse response = registryService.testConnection(id);
        return ResponseEntity.ok(response);
    }
}
