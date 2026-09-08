package com.example.prodsupport.starter;

import com.example.prodsupport.starter.config.SupportAgentAutoConfiguration;
import com.example.prodsupport.starter.controller.SupportInfoController;
import com.example.prodsupport.starter.model.SupportInfoResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.health.HealthContributorAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.health.HealthEndpointAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class SupportInfoEndpointTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SupportAgentAutoConfiguration.class,
                    HealthContributorAutoConfiguration.class,
                    HealthEndpointAutoConfiguration.class
            ));

    @Test
    void shouldNotRegisterControllerWhenDisabledOrMissing() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(SupportInfoController.class);
        });

        contextRunner.withPropertyValues("prod-support.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(SupportInfoController.class);
                });
    }

    @Test
    void shouldRegisterControllerAndReturnMetadataWhenEnabled() {
        contextRunner
                .withPropertyValues(
                        "prod-support.enabled=true",
                        "prod-support.application.name=payment-service",
                        "prod-support.application.team=payments",
                        "prod-support.application.description=Demo payment processing service",
                        "prod-support.application.environment=local",
                        "prod-support.diagnostics.health=true"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(SupportInfoController.class);
                    SupportInfoController controller = context.getBean(SupportInfoController.class);

                    ResponseEntity<SupportInfoResponse> response = controller.getSupportInfo();
                    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

                    SupportInfoResponse body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getApplicationName()).isEqualTo("payment-service");
                    assertThat(body.getTeam()).isEqualTo("payments");
                    assertThat(body.getDescription()).isEqualTo("Demo payment processing service");
                    assertThat(body.getEnvironment()).isEqualTo("local");
                    assertThat(body.getStatus()).isEqualTo("UP");
                });
    }
}
