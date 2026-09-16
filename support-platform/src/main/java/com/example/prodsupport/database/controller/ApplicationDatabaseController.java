package com.example.prodsupport.database.controller;

import com.example.prodsupport.common.exception.ApplicationNotFoundException;
import com.example.prodsupport.database.dto.DatabaseConfigDto;
import com.example.prodsupport.database.dto.DatabaseConfigRequest;
import com.example.prodsupport.database.model.ApplicationDatabaseDiagnostics;
import com.example.prodsupport.database.service.ApplicationDatabaseConfigService;
import com.example.prodsupport.database.service.ApplicationDatabaseDiagnosticService;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.infrastructure.repository.RegisteredApplicationRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/applications/{id}")
public class ApplicationDatabaseController {

    private final ApplicationDatabaseConfigService configService;
    private final ApplicationDatabaseDiagnosticService diagnosticService;
    private final RegisteredApplicationRepository applicationRepository;

    public ApplicationDatabaseController(ApplicationDatabaseConfigService configService,
                                         ApplicationDatabaseDiagnosticService diagnosticService,
                                         RegisteredApplicationRepository applicationRepository) {
        this.configService = configService;
        this.diagnosticService = diagnosticService;
        this.applicationRepository = applicationRepository;
    }

    @PutMapping("/database")
    public ResponseEntity<DatabaseConfigDto> configureDatabase(
            @PathVariable Long id,
            @Valid @RequestBody DatabaseConfigRequest request) {
        DatabaseConfigDto dto = configService.saveOrUpdateDatabaseConfig(id, request);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/database")
    public ResponseEntity<DatabaseConfigDto> getDatabaseConfig(@PathVariable Long id) {
        return configService.getDatabaseConfig(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/diagnostics/database")
    public ResponseEntity<ApplicationDatabaseDiagnostics> getDatabaseDiagnostics(@PathVariable Long id) {
        RegisteredApplication app = applicationRepository.findById(id)
                .orElseThrow(() -> new ApplicationNotFoundException(id));

        ApplicationDatabaseDiagnostics diagnostics = diagnosticService.runDiagnostics(app);
        return ResponseEntity.ok(diagnostics);
    }
}
