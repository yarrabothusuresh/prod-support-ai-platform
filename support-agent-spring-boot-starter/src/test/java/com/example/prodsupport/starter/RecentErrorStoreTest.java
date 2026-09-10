package com.example.prodsupport.starter;

import com.example.prodsupport.starter.model.SupportError;
import com.example.prodsupport.starter.store.RecentErrorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecentErrorStoreTest {

    private RecentErrorStore errorStore;

    @BeforeEach
    void setUp() {
        errorStore = new RecentErrorStore(5); // Small capacity for eviction tests
    }

    @Test
    void shouldRecordAndRetrieveErrorsInReverseChronologicalOrder() {
        errorStore.recordError("WARN", "TimeoutWarning", "Request took too long");
        errorStore.recordError("ERROR", "DatabaseException", "Database connection lost");

        List<SupportError> errors = errorStore.getRecentErrors(10);
        assertThat(errors).hasSize(2);
        assertThat(errors.get(0).type()).isEqualTo("DatabaseException");
        assertThat(errors.get(1).type()).isEqualTo("TimeoutWarning");
    }

    @Test
    void shouldEvictOldestErrorsWhenCapacityExceeded() {
        for (int i = 1; i <= 7; i++) {
            errorStore.recordError("ERROR", "ErrorType" + i, "Error message " + i);
        }

        assertThat(errorStore.size()).isEqualTo(5);
        List<SupportError> errors = errorStore.getRecentErrors(10);
        assertThat(errors).hasSize(5);
        // Most recent should be 7 down to 3
        assertThat(errors.get(0).type()).isEqualTo("ErrorType7");
        assertThat(errors.get(4).type()).isEqualTo("ErrorType3");
    }

    @Test
    void shouldRespectRequestedLimit() {
        for (int i = 1; i <= 5; i++) {
            errorStore.recordError("ERROR", "ErrorType" + i, "Message " + i);
        }

        List<SupportError> errors = errorStore.getRecentErrors(2);
        assertThat(errors).hasSize(2);
        assertThat(errors.get(0).type()).isEqualTo("ErrorType5");
        assertThat(errors.get(1).type()).isEqualTo("ErrorType4");
    }

    @Test
    void shouldSanitizePasswordsAndSecrets() {
        String raw = "Failed to connect to db: password=superSecretPassword123 with user=admin";
        String sanitized = RecentErrorStore.sanitize(raw);

        assertThat(sanitized).doesNotContain("superSecretPassword123");
        assertThat(sanitized).contains("password=***REDACTED***");
    }

    @Test
    void shouldSanitizeBearerTokensAndAuthHeaders() {
        String raw = "Call to downstream returned 401 with Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.test and token=secretToken456";
        String sanitized = RecentErrorStore.sanitize(raw);

        assertThat(sanitized).doesNotContain("eyJhbGciOiJIUzI1NiJ9.test");
        assertThat(sanitized).doesNotContain("secretToken456");
        assertThat(sanitized).contains("Bearer ***REDACTED***");
        assertThat(sanitized).contains("token=***REDACTED***");
    }

    @Test
    void shouldSanitizeJdbcCredentials() {
        String raw = "Failed pool connection jdbc:postgresql://dbuser:mypassword@localhost:5432/proddb";
        String sanitized = RecentErrorStore.sanitize(raw);

        assertThat(sanitized).doesNotContain("mypassword");
        assertThat(sanitized).contains("jdbc:postgresql://dbuser:***REDACTED***@localhost:5432/proddb");
    }

    @Test
    void shouldSanitizeCreditCardsAndEmails() {
        String raw = "Failed processing for customer john.doe@example.com with card 4111222233334444";
        String sanitized = RecentErrorStore.sanitize(raw);

        assertThat(sanitized).doesNotContain("john.doe@example.com");
        assertThat(sanitized).doesNotContain("4111222233334444");
        assertThat(sanitized).contains("***REDACTED_EMAIL***");
        assertThat(sanitized).contains("***REDACTED_CC***");
    }

    @Test
    void shouldSuppressMultiLineStackTrace() {
        String raw = """
                NullPointerException: User id was null
                \tat com.example.service.UserService.process(UserService.java:42)
                \tat com.example.controller.UserController.handle(UserController.java:18)
                \tat org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:1089)
                """;
        String sanitized = RecentErrorStore.sanitize(raw);

        assertThat(sanitized).contains("NullPointerException: User id was null");
        assertThat(sanitized).contains("(stack trace suppressed)");
        assertThat(sanitized).doesNotContain("org.springframework.web.servlet.DispatcherServlet");
    }
}
