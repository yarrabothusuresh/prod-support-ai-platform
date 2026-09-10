package com.example.prodsupport;

import com.example.prodsupport.ai.config.AiProperties;
import com.example.prodsupport.ai.model.ApplicationSupportContext;
import com.example.prodsupport.ai.service.ApplicationContextService;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationContextServiceTest {

    private MockWebServer server;
    private ApplicationContextService contextService;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        AiProperties properties = new AiProperties();
        properties.setContextTimeoutSeconds(2);

        RestClient restClient = RestClient.builder().build();
        contextService = new ApplicationContextService(restClient, properties);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    @DisplayName("Should collect UP status for all endpoints when services are healthy")
    void shouldCollectAllEndpointsSuccessfully() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"applicationName\":\"payment-service\",\"status\":\"UP\"}"));

        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"status\":\"UP\"}"));

        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"applicationName\":\"payment-service\",\"errors\":[{\"level\":\"ERROR\",\"type\":\"NullPointer\",\"message\":\"Something failed\"}]}"));

        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"applicationName\":\"payment-service\",\"dependencies\":[{\"name\":\"postgres-db\",\"type\":\"DATABASE\",\"status\":\"UP\"}]}"));

        String baseUrl = server.url("/").toString();

        ApplicationSupportContext context = contextService.collectContext(
                "payment-service", "payments", "local", "Payment Service", baseUrl, null, null
        );

        assertThat(context.applicationName()).isEqualTo("payment-service");
        assertThat(context.supportStatus()).isEqualTo("UP");
        assertThat(context.actuatorStatus()).isEqualTo("UP");
        assertThat(context.supportInfoAvailable()).isTrue();
        assertThat(context.healthAvailable()).isTrue();
        assertThat(context.errorsAvailable()).isTrue();
        assertThat(context.dependenciesAvailable()).isTrue();
        assertThat(context.recentErrors()).hasSize(1);
        assertThat(context.recentErrors().get(0).type()).isEqualTo("NullPointer");
        assertThat(context.dependencies()).hasSize(1);
        assertThat(context.dependencies().get(0).name()).isEqualTo("postgres-db");
        assertThat(context.warnings()).isEmpty();
    }

    @Test
    @DisplayName("Should handle partial failure when actuator health returns 500 error")
    void shouldHandleActuatorHealthFailureResiliently() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"applicationName\":\"payment-service\",\"status\":\"UP\"}"));

        server.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"));

        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"applicationName\":\"payment-service\",\"errors\":[]}"));

        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"applicationName\":\"payment-service\",\"dependencies\":[]}"));

        String baseUrl = server.url("/").toString();

        ApplicationSupportContext context = contextService.collectContext(
                "payment-service", "payments", "local", "Payment Service", baseUrl, null, null
        );

        assertThat(context.supportStatus()).isEqualTo("UP");
        assertThat(context.supportInfoAvailable()).isTrue();
        assertThat(context.actuatorStatus()).isEqualTo("UNKNOWN");
        assertThat(context.healthAvailable()).isFalse();
        assertThat(context.errorsAvailable()).isTrue();
        assertThat(context.dependenciesAvailable()).isTrue();
        assertThat(context.warnings()).hasSize(1);
        assertThat(context.warnings().get(0)).contains("Unable to retrieve actuator health");
    }

    @Test
    @DisplayName("Should handle partial failure when support info returns 404 error")
    void shouldHandleSupportInfoFailureResiliently() {
        server.enqueue(new MockResponse()
                .setResponseCode(404)
                .setBody("Not Found"));

        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"status\":\"UP\"}"));

        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"applicationName\":\"payment-service\",\"errors\":[]}"));

        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"applicationName\":\"payment-service\",\"dependencies\":[]}"));

        String baseUrl = server.url("/").toString();

        ApplicationSupportContext context = contextService.collectContext(
                "payment-service", "payments", "local", "Payment Service", baseUrl, null, null
        );

        assertThat(context.supportStatus()).isEqualTo("UNKNOWN");
        assertThat(context.supportInfoAvailable()).isFalse();
        assertThat(context.actuatorStatus()).isEqualTo("UP");
        assertThat(context.healthAvailable()).isTrue();
        assertThat(context.errorsAvailable()).isTrue();
        assertThat(context.dependenciesAvailable()).isTrue();
        assertThat(context.warnings()).hasSize(1);
        assertThat(context.warnings().get(0)).contains("Unable to retrieve support info");
    }

    @Test
    @DisplayName("Should handle partial failure when diagnostics endpoints return 404 or fail")
    void shouldHandleDiagnosticsEndpointsFailureResiliently() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"applicationName\":\"payment-service\",\"status\":\"UP\"}"));

        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"status\":\"UP\"}"));

        server.enqueue(new MockResponse()
                .setResponseCode(404)
                .setBody("Not Found"));

        server.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"));

        String baseUrl = server.url("/").toString();

        ApplicationSupportContext context = contextService.collectContext(
                "payment-service", "payments", "local", "Payment Service", baseUrl, null, null
        );

        assertThat(context.supportStatus()).isEqualTo("UP");
        assertThat(context.actuatorStatus()).isEqualTo("UP");
        assertThat(context.errorsAvailable()).isFalse();
        assertThat(context.dependenciesAvailable()).isFalse();
        assertThat(context.warnings()).hasSize(2);
        assertThat(context.warnings().get(0)).contains("Unable to retrieve recent errors");
        assertThat(context.warnings().get(1)).contains("Unable to retrieve dependencies");
    }

    @Test
    @DisplayName("Should handle complete failure when host is unreachable")
    void shouldHandleUnreachableHost() {
        // Port 59999 where nothing is listening
        String unreachableUrl = "http://localhost:59999";

        ApplicationSupportContext context = contextService.collectContext(
                "payment-service", "payments", "local", "Payment Service", unreachableUrl, null, null
        );

        assertThat(context.supportStatus()).isEqualTo("UNKNOWN");
        assertThat(context.actuatorStatus()).isEqualTo("UNKNOWN");
        assertThat(context.supportInfoAvailable()).isFalse();
        assertThat(context.healthAvailable()).isFalse();
        assertThat(context.errorsAvailable()).isFalse();
        assertThat(context.dependenciesAvailable()).isFalse();
        assertThat(context.warnings()).hasSize(4);
    }
}
