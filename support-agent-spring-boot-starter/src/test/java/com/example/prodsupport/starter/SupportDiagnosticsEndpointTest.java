package com.example.prodsupport.starter;

import com.example.prodsupport.starter.config.SupportAgentAutoConfiguration;
import com.example.prodsupport.starter.controller.SupportDiagnosticsController;
import com.example.prodsupport.starter.model.SupportDependenciesResponse;
import com.example.prodsupport.starter.model.SupportErrorsResponse;
import com.example.prodsupport.starter.service.DependencyHealthService;
import com.example.prodsupport.starter.store.RecentErrorStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class SupportDiagnosticsEndpointTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SupportAgentAutoConfiguration.class));

    @Test
    void shouldRegisterDiagnosticsControllerAndExposeEndpoints() {
        contextRunner
                .withPropertyValues(
                        "prod-support.enabled=true",
                        "prod-support.application.name=payment-service",
                        "prod-support.dependencies[0].name=postgres",
                        "prod-support.dependencies[0].type=DATABASE",
                        "prod-support.dependencies[0].status=UP",
                        "prod-support.dependencies[1].name=notification-service",
                        "prod-support.dependencies[1].type=HTTP",
                        "prod-support.dependencies[1].status=UP"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(SupportDiagnosticsController.class);
                    assertThat(context).hasSingleBean(RecentErrorStore.class);
                    assertThat(context).hasSingleBean(DependencyHealthService.class);

                    SupportDiagnosticsController controller = context.getBean(SupportDiagnosticsController.class);
                    RecentErrorStore errorStore = context.getBean(RecentErrorStore.class);
                    DependencyHealthService healthService = context.getBean(DependencyHealthService.class);

                    // Test errors endpoint initially empty
                    ResponseEntity<SupportErrorsResponse> emptyErrorsResponse = controller.getRecentErrors(20);
                    assertThat(emptyErrorsResponse.getStatusCode().is2xxSuccessful()).isTrue();
                    assertThat(emptyErrorsResponse.getBody()).isNotNull();
                    assertThat(emptyErrorsResponse.getBody().applicationName()).isEqualTo("payment-service");
                    assertThat(emptyErrorsResponse.getBody().errors()).isEmpty();

                    // Record an error and verify retrieval
                    errorStore.recordError("ERROR", "DatabaseConnectionException", "Failed to connect to database");
                    ResponseEntity<SupportErrorsResponse> errorsResponse = controller.getRecentErrors(20);
                    assertThat(errorsResponse.getBody().errors()).hasSize(1);
                    assertThat(errorsResponse.getBody().errors().get(0).type()).isEqualTo("DatabaseConnectionException");

                    // Test dependencies endpoint
                    ResponseEntity<SupportDependenciesResponse> depsResponse = controller.getDependencies();
                    assertThat(depsResponse.getStatusCode().is2xxSuccessful()).isTrue();
                    assertThat(depsResponse.getBody()).isNotNull();
                    assertThat(depsResponse.getBody().applicationName()).isEqualTo("payment-service");
                    assertThat(depsResponse.getBody().dependencies()).hasSize(2);
                    assertThat(depsResponse.getBody().dependencies().get(0).name()).isEqualTo("postgres");
                    assertThat(depsResponse.getBody().dependencies().get(0).status()).isEqualTo("UP");

                    // Test dependency override simulation
                    healthService.setDependencyOverride("notification-service", "DOWN");
                    ResponseEntity<SupportDependenciesResponse> overriddenResponse = controller.getDependencies();
                    assertThat(overriddenResponse.getBody().dependencies().get(1).name()).isEqualTo("notification-service");
                    assertThat(overriddenResponse.getBody().dependencies().get(1).status()).isEqualTo("DOWN");
                });
    }
}
