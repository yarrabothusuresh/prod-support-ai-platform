package com.example.prodsupport;

import com.example.prodsupport.ai.config.AiProperties;
import com.example.prodsupport.ai.tools.ApplicationInfoAiTool;
import com.example.prodsupport.ai.tools.DependencyAiTool;
import com.example.prodsupport.ai.tools.HealthAiTool;
import com.example.prodsupport.ai.tools.RecentErrorsAiTool;
import com.example.prodsupport.ai.tools.audit.ToolExecutionAudit;
import com.example.prodsupport.ai.tools.audit.ToolExecutionAuditor;
import com.example.prodsupport.ai.tools.context.InvestigationContext;
import com.example.prodsupport.ai.tools.context.InvestigationContextHolder;
import com.example.prodsupport.ai.tools.model.*;
import com.example.prodsupport.ai.tools.security.ApplicationAccessValidator;
import com.example.prodsupport.application.service.DiagnosticService;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.infrastructure.repository.RegisteredApplicationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
class DiagnosticToolExecutionTest {

    @Autowired
    private ApplicationInfoAiTool appInfoTool;

    @Autowired
    private HealthAiTool healthTool;

    @Autowired
    private RecentErrorsAiTool errorsTool;

    @Autowired
    private DependencyAiTool dependencyTool;

    @Autowired
    private RegisteredApplicationRepository repository;

    @Autowired
    private ToolExecutionAuditor auditor;

    @Autowired
    private ApplicationAccessValidator accessValidator;

    @Autowired
    private AiProperties aiProperties;

    @MockBean
    private DiagnosticService diagnosticService;

    private RegisteredApplication paymentApp;
    private RegisteredApplication disabledApp;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        auditor.clearHistory();

        paymentApp = new RegisteredApplication(
                "payment-service",
                "payments",
                "local",
                "Demo payment service",
                "http://localhost:8081",
                "http://localhost:8081/actuator/health",
                "http://localhost:8081/support/info",
                true
        );
        repository.save(paymentApp);

        disabledApp = new RegisteredApplication(
                "legacy-service",
                "legacy",
                "local",
                "Disabled legacy service",
                "http://localhost:8089",
                "http://localhost:8089/actuator/health",
                "http://localhost:8089/support/info",
                false
        );
        repository.save(disabledApp);
    }

    @AfterEach
    void tearDown() {
        InvestigationContextHolder.clearContext();
    }

    @Test
    @DisplayName("Application Info Tool should return application metadata successfully")
    void shouldExecuteApplicationInfoToolSuccessfully() {
        when(diagnosticService.getApplicationInfo(any())).thenReturn(
                new ApplicationInfoData("payment-service", "payments", "local", "Demo payment service", true)
        );

        ApplicationInfoRequest request = new ApplicationInfoRequest("payment-service", "local");
        ToolExecutionResult<ApplicationInfoData> result = appInfoTool.apply(request);

        assertThat(result.success()).isTrue();
        assertThat(result.toolName()).isEqualTo("get_application_info");
        assertThat(result.data()).isNotNull();
        assertThat(result.data().applicationName()).isEqualTo("payment-service");
        assertThat(result.data().team()).isEqualTo("payments");

        // Verify audit log
        List<ToolExecutionAudit> history = auditor.getAuditHistory();
        assertThat(history).isNotEmpty();
        ToolExecutionAudit audit = history.getLast();
        assertThat(audit.toolName()).isEqualTo("get_application_info");
        assertThat(audit.applicationName()).isEqualTo("payment-service");
        assertThat(audit.success()).isTrue();
    }

    @Test
    @DisplayName("Health Tool should execute and return application health status")
    void shouldExecuteHealthToolSuccessfully() {
        when(diagnosticService.checkHealth(any())).thenReturn(
                new ApplicationHealthData("UP", "/actuator/health", "2026-09-13T20:00:00Z")
        );

        ApplicationHealthRequest request = new ApplicationHealthRequest("payment-service", "local");
        ToolExecutionResult<ApplicationHealthData> result = healthTool.apply(request);

        assertThat(result.success()).isTrue();
        assertThat(result.toolName()).isEqualTo("check_application_health");
        assertThat(result.data().status()).isEqualTo("UP");

        List<ToolExecutionAudit> history = auditor.getAuditHistory();
        assertThat(history).isNotEmpty();
        assertThat(history.getLast().success()).isTrue();
    }

    @Test
    @DisplayName("Recent Errors Tool should enforce limit validation (1 to 50)")
    void shouldValidateLimitInRecentErrorsTool() {
        // Limit > 50 rejected
        RecentErrorsRequest invalidHigh = new RecentErrorsRequest("payment-service", "local", 100);
        ToolExecutionResult<RecentErrorsData> highResult = errorsTool.apply(invalidHigh);
        assertThat(highResult.success()).isFalse();
        assertThat(highResult.warning()).contains("Invalid limit: 100");

        // Limit < 1 rejected
        RecentErrorsRequest invalidLow = new RecentErrorsRequest("payment-service", "local", 0);
        ToolExecutionResult<RecentErrorsData> lowResult = errorsTool.apply(invalidLow);
        assertThat(lowResult.success()).isFalse();
        assertThat(lowResult.warning()).contains("Invalid limit: 0");

        // Valid limit returns data
        when(diagnosticService.getRecentErrors(any(), eq(5))).thenReturn(
                new RecentErrorsData(1, List.of(new SanitizedErrorDto("2026-09-13T20:00:00Z", "TimeoutException", "DB timeout")))
        );

        RecentErrorsRequest validReq = new RecentErrorsRequest("payment-service", "local", 5);
        ToolExecutionResult<RecentErrorsData> validResult = errorsTool.apply(validReq);
        assertThat(validResult.success()).isTrue();
        assertThat(validResult.data().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Dependency Tool should execute and return downstream dependencies")
    void shouldExecuteDependencyToolSuccessfully() {
        when(diagnosticService.checkDependencies(any())).thenReturn(
                new DependenciesData(List.of(
                        new DependencyItemDto("postgres-db", "DATABASE", "DOWN"),
                        new DependencyItemDto("notification-service", "HTTP", "UP")
                ))
        );

        DependenciesRequest request = new DependenciesRequest("payment-service", "local");
        ToolExecutionResult<DependenciesData> result = dependencyTool.apply(request);

        assertThat(result.success()).isTrue();
        assertThat(result.data().dependencies()).hasSize(2);
        assertThat(result.data().dependencies().get(0).status()).isEqualTo("DOWN");
    }

    @Test
    @DisplayName("Tool execution against disabled application should be rejected safely")
    void shouldRejectDisabledApplication() {
        ApplicationHealthRequest request = new ApplicationHealthRequest("legacy-service", "local");
        ToolExecutionResult<ApplicationHealthData> result = healthTool.apply(request);

        assertThat(result.success()).isFalse();
        assertThat(result.warning()).contains("disabled and cannot be diagnosed");
    }

    @Test
    @DisplayName("Tool execution against unknown application should be rejected safely")
    void shouldRejectUnknownApplication() {
        ApplicationHealthRequest request = new ApplicationHealthRequest("non-existent-service", "local");
        ToolExecutionResult<ApplicationHealthData> result = healthTool.apply(request);

        assertThat(result.success()).isFalse();
        assertThat(result.warning()).contains("not registered");
    }

    @Test
    @DisplayName("Tool execution should handle timeout/failure without crashing")
    void shouldHandleToolTimeoutGracefully() {
        when(diagnosticService.checkHealth(any())).thenThrow(
                new DiagnosticService.DiagnosticExecutionException("Connection timed out", new RuntimeException("SocketTimeoutException"))
        );
        when(diagnosticService.cleanErrorMessage(any())).thenReturn("Connection timed out");

        ApplicationHealthRequest request = new ApplicationHealthRequest("payment-service", "local");
        ToolExecutionResult<ApplicationHealthData> result = healthTool.apply(request);

        assertThat(result.success()).isFalse();
        assertThat(result.warning()).contains("Unable to retrieve application health (Connection timed out)");

        List<ToolExecutionAudit> history = auditor.getAuditHistory();
        assertThat(history.getLast().success()).isFalse();
        assertThat(history.getLast().failureReason()).contains("Connection timed out");
    }

    @Test
    @DisplayName("Infinite tool loop guard should halt execution when maxToolCalls is reached")
    void shouldHaltWhenMaxToolCallsReached() {
        InvestigationContext context = new InvestigationContext("payment-service", "local", "Test question", 2);
        InvestigationContextHolder.setContext(context);

        when(diagnosticService.checkHealth(any())).thenReturn(
                new ApplicationHealthData("UP", "/actuator/health", "2026-09-13T20:00:00Z")
        );

        ApplicationHealthRequest req = new ApplicationHealthRequest("payment-service", "local");

        // 1st call: OK
        ToolExecutionResult<ApplicationHealthData> res1 = healthTool.apply(req);
        assertThat(res1.success()).isTrue();
        assertThat(context.getExecutionCount()).isEqualTo(1);

        // 2nd call: OK
        ToolExecutionResult<ApplicationHealthData> res2 = healthTool.apply(req);
        assertThat(res2.success()).isTrue();
        assertThat(context.getExecutionCount()).isEqualTo(2);

        // 3rd call: BLOCKED by loop guard!
        ToolExecutionResult<ApplicationHealthData> res3 = healthTool.apply(req);
        assertThat(res3.success()).isFalse();
        assertThat(res3.warning()).contains("Investigation reached maximum diagnostic steps");
        assertThat(context.getWarnings()).contains("Investigation reached maximum diagnostic steps. Answer is based on currently available evidence.");
    }
}
