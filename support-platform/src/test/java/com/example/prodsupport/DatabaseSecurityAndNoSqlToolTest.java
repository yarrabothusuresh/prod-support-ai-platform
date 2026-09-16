package com.example.prodsupport;

import com.example.prodsupport.ai.tools.DiagnosticToolRegistry;
import com.example.prodsupport.ai.tools.model.DatabaseDiagnosticToolRequest;
import com.example.prodsupport.ai.tools.security.ToolAllowlist;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Field;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DatabaseSecurityAndNoSqlToolTest {

    @Autowired
    private DiagnosticToolRegistry toolRegistry;

    @Test
    @DisplayName("Verify absolutely NO generic SQL or query execution AI tools exist")
    void testNoGenericSqlAiToolExists() {
        Set<String> registeredTools = toolRegistry.getRegisteredToolNames();

        // Prohibited tool patterns
        String[] prohibitedPatterns = {
                "execute_sql",
                "run_sql",
                "query_database",
                "execute_query",
                "execute_database_command",
                "sql_query",
                "db_execute"
        };

        for (String pattern : prohibitedPatterns) {
            assertThat(registeredTools).doesNotContain(pattern);
            assertThat(ToolAllowlist.isAllowed(pattern)).isFalse();
        }
    }

    @Test
    @DisplayName("Verify DatabaseDiagnosticToolRequest accepts ONLY applicationName and environment (No SQL, No JDBC URL, No Credentials)")
    void testDatabaseToolRequestInputSecurity() {
        Field[] fields = DatabaseDiagnosticToolRequest.class.getDeclaredFields();

        for (Field field : fields) {
            String fieldName = field.getName().toLowerCase();
            assertThat(fieldName).isNotIn(
                    "sql",
                    "query",
                    "jdbcurl",
                    "url",
                    "host",
                    "port",
                    "username",
                    "user",
                    "password",
                    "secret",
                    "credential"
            );
        }

        // Verify strictly only 2 fields exist
        assertThat(fields).hasSize(2);
        assertThat(fields).extracting(Field::getName).containsExactlyInAnyOrder("applicationName", "environment");
    }
}
