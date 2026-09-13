package com.example.prodsupport.knowledge;

import com.example.prodsupport.knowledge.service.SecretDetector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecretDetectorTest {

    private final SecretDetector detector = new SecretDetector();

    @Test
    @DisplayName("Clean document passes secret scan without error")
    void cleanDocumentPasses() {
        String cleanDoc = """
                # Payment Service Runbook
                When database connections are exhausted:
                1. Inspect active connection pool metrics.
                2. Scale read replicas if read traffic spiked.
                3. Restart stuck worker instances gracefully.
                """;

        assertThat(detector.containsSecrets(cleanDoc)).isFalse();
        List<String> findings = detector.findSecretMatches(cleanDoc);
        assertThat(findings).isEmpty();
    }

    @Test
    @DisplayName("Detects hardcoded password assignment")
    void detectsPassword() {
        String leakedDoc = "spring.datasource.password=super_secret_12345";
        assertThat(detector.containsSecrets(leakedDoc)).isTrue();
        List<String> findings = detector.findSecretMatches(leakedDoc);
        assertThat(findings).isNotEmpty();
    }

    @Test
    @DisplayName("Detects RSA private key block")
    void detectsPrivateKey() {
        String leakedDoc = """
                -----BEGIN RSA PRIVATE KEY-----
                MIIEowIBAAKCAQEA0Y1+example+key+data...
                -----END RSA PRIVATE KEY-----
                """;
        assertThat(detector.containsSecrets(leakedDoc)).isTrue();
        List<String> findings = detector.findSecretMatches(leakedDoc);
        assertThat(findings).isNotEmpty();
    }

    @Test
    @DisplayName("Detects Bearer token")
    void detectsBearerToken() {
        String leakedDoc = "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.e30.t-ID";
        assertThat(detector.containsSecrets(leakedDoc)).isTrue();
        List<String> findings = detector.findSecretMatches(leakedDoc);
        assertThat(findings).isNotEmpty();
    }
}
