package com.example.prodsupport;

import com.example.prodsupport.application.dto.TestConnectionResponse;
import com.example.prodsupport.infrastructure.client.SupportClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SupportClientTest {

    private MockRestServiceServer mockServer;
    private SupportClient supportClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        supportClient = new SupportClient(builder);
    }

    @Test
    void shouldReturnSuccessWhenRemoteEndpointAnswers() {
        mockServer.expect(requestTo("http://localhost:8081/support/info"))
                .andRespond(withSuccess("{\"applicationName\":\"payment-service\",\"status\":\"UP\"}", MediaType.APPLICATION_JSON));

        TestConnectionResponse response = supportClient.testConnection("http://localhost:8081/support/info");

        assertThat(response.isConnected()).isTrue();
        assertThat(response.getApplicationName()).isEqualTo("payment-service");
        assertThat(response.getStatus()).isEqualTo("UP");
        assertThat(response.getResponseTimeMs()).isNotNull();
        assertThat(response.getError()).isNull();
        mockServer.verify();
    }

    @Test
    void shouldReturnCleanFailureWhenRemoteEndpointReturns500() {
        mockServer.expect(requestTo("http://localhost:8081/support/info"))
                .andRespond(withServerError());

        TestConnectionResponse response = supportClient.testConnection("http://localhost:8081/support/info");

        assertThat(response.isConnected()).isFalse();
        assertThat(response.getError()).isEqualTo("Remote service error (500)");
        assertThat(response.getApplicationName()).isNull();
        mockServer.verify();
    }
}
