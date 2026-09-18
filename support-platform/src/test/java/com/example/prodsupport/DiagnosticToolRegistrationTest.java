package com.example.prodsupport;

import com.example.prodsupport.ai.tools.ApplicationInfoAiTool;
import com.example.prodsupport.ai.tools.DependencyAiTool;
import com.example.prodsupport.ai.tools.DiagnosticToolRegistry;
import com.example.prodsupport.ai.tools.HealthAiTool;
import com.example.prodsupport.ai.tools.KnowledgeBaseAiTool;
import com.example.prodsupport.ai.tools.RecentErrorsAiTool;
import com.example.prodsupport.ai.tools.security.ToolAllowlist;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DiagnosticToolRegistrationTest {

    @Autowired
    private DiagnosticToolRegistry toolRegistry;

    @Test
    @DisplayName("Verify all 15 approved diagnostic tools are registered in tool registry")
    void shouldRegisterAllFifteenApprovedTools() {
        Set<String> registeredNames = toolRegistry.getRegisteredToolNames();

        assertThat(registeredNames)
                .containsExactlyInAnyOrder(
                        ToolAllowlist.TOOL_GET_APPLICATION_INFO,
                        ToolAllowlist.TOOL_CHECK_APPLICATION_HEALTH,
                        ToolAllowlist.TOOL_GET_RECENT_ERRORS,
                        ToolAllowlist.TOOL_CHECK_DEPENDENCIES,
                        ToolAllowlist.TOOL_SEARCH_KNOWLEDGE_BASE,
                        ToolAllowlist.TOOL_CHECK_KAFKA_CLUSTER,
                        ToolAllowlist.TOOL_CHECK_KAFKA_CONSUMER_GROUP,
                        ToolAllowlist.TOOL_CHECK_KAFKA_CONSUMER_LAG,
                        ToolAllowlist.TOOL_GET_KAFKA_TOPIC_INFO,
                        ToolAllowlist.TOOL_CHECK_DATABASE_HEALTH,
                        ToolAllowlist.TOOL_CHECK_DATABASE_CONNECTION_POOL,
                        ToolAllowlist.TOOL_CHECK_DATABASE_ACTIVITY,
                        ToolAllowlist.TOOL_SEARCH_APPLICATION_ERRORS,
                        ToolAllowlist.TOOL_GET_ERROR_PATTERN_SUMMARY,
                        ToolAllowlist.TOOL_GET_APPLICATION_LOG_TIMELINE
                );

        List<FunctionCallback> callbacks = toolRegistry.getAllCallbacks();
        assertThat(callbacks).hasSize(15);
    }


    @Test
    @DisplayName("Verify each tool has a clear non-empty description and name")
    void shouldHaveClearDescriptions() {
        FunctionCallback appInfo = toolRegistry.getCallback(ToolAllowlist.TOOL_GET_APPLICATION_INFO);
        assertThat(appInfo).isNotNull();
        assertThat(appInfo.getName()).isEqualTo(ApplicationInfoAiTool.TOOL_NAME);
        assertThat(appInfo.getDescription()).contains("metadata and registered configuration");

        FunctionCallback health = toolRegistry.getCallback(ToolAllowlist.TOOL_CHECK_APPLICATION_HEALTH);
        assertThat(health).isNotNull();
        assertThat(health.getName()).isEqualTo(HealthAiTool.TOOL_NAME);
        assertThat(health.getDescription()).contains("current application health and actuator status");

        FunctionCallback errors = toolRegistry.getCallback(ToolAllowlist.TOOL_GET_RECENT_ERRORS);
        assertThat(errors).isNotNull();
        assertThat(errors.getName()).isEqualTo(RecentErrorsAiTool.TOOL_NAME);
        assertThat(errors.getDescription()).contains("recent recorded error diagnostics");

        FunctionCallback deps = toolRegistry.getCallback(ToolAllowlist.TOOL_CHECK_DEPENDENCIES);
        assertThat(deps).isNotNull();
        assertThat(deps.getName()).isEqualTo(DependencyAiTool.TOOL_NAME);
        assertThat(deps.getDescription()).contains("downstream dependency health");

        FunctionCallback knowledge = toolRegistry.getCallback(ToolAllowlist.TOOL_SEARCH_KNOWLEDGE_BASE);
        assertThat(knowledge).isNotNull();
        assertThat(knowledge.getName()).isEqualTo(KnowledgeBaseAiTool.TOOL_NAME);
        assertThat(knowledge.getDescription()).contains("knowledge base");

        FunctionCallback dbHealth = toolRegistry.getCallback(ToolAllowlist.TOOL_CHECK_DATABASE_HEALTH);
        assertThat(dbHealth).isNotNull();
        assertThat(dbHealth.getDescription()).contains("database");

        FunctionCallback dbPool = toolRegistry.getCallback(ToolAllowlist.TOOL_CHECK_DATABASE_CONNECTION_POOL);
        assertThat(dbPool).isNotNull();
        assertThat(dbPool.getDescription()).contains("connection pool");

        FunctionCallback dbActivity = toolRegistry.getCallback(ToolAllowlist.TOOL_CHECK_DATABASE_ACTIVITY);
        assertThat(dbActivity).isNotNull();
        assertThat(dbActivity.getDescription()).contains("database activity");

        FunctionCallback logErrors = toolRegistry.getCallback(ToolAllowlist.TOOL_SEARCH_APPLICATION_ERRORS);
        assertThat(logErrors).isNotNull();
        assertThat(logErrors.getDescription()).contains("ERROR and WARN logs");

        FunctionCallback logPatterns = toolRegistry.getCallback(ToolAllowlist.TOOL_GET_ERROR_PATTERN_SUMMARY);
        assertThat(logPatterns).isNotNull();
        assertThat(logPatterns.getDescription()).contains("aggregated counts");

        FunctionCallback logTimeline = toolRegistry.getCallback(ToolAllowlist.TOOL_GET_APPLICATION_LOG_TIMELINE);
        assertThat(logTimeline).isNotNull();
        assertThat(logTimeline.getDescription()).contains("chronological timeline");
    }

    @Test
    @DisplayName("Verify ToolAllowlist strictly allows approved tools only")
    void shouldValidateToolAllowlist() {
        assertThat(ToolAllowlist.isAllowed("get_application_info")).isTrue();
        assertThat(ToolAllowlist.isAllowed("check_application_health")).isTrue();
        assertThat(ToolAllowlist.isAllowed("get_recent_errors")).isTrue();
        assertThat(ToolAllowlist.isAllowed("check_dependencies")).isTrue();
        assertThat(ToolAllowlist.isAllowed("search_knowledge_base")).isTrue();
        assertThat(ToolAllowlist.isAllowed("check_kafka_cluster")).isTrue();
        assertThat(ToolAllowlist.isAllowed("check_kafka_consumer_group")).isTrue();
        assertThat(ToolAllowlist.isAllowed("check_kafka_consumer_lag")).isTrue();
        assertThat(ToolAllowlist.isAllowed("get_kafka_topic_info")).isTrue();
        assertThat(ToolAllowlist.isAllowed("check_database_health")).isTrue();
        assertThat(ToolAllowlist.isAllowed("check_database_connection_pool")).isTrue();
        assertThat(ToolAllowlist.isAllowed("check_database_activity")).isTrue();
        assertThat(ToolAllowlist.isAllowed("search_application_errors")).isTrue();
        assertThat(ToolAllowlist.isAllowed("get_error_pattern_summary")).isTrue();
        assertThat(ToolAllowlist.isAllowed("get_application_log_timeline")).isTrue();

        // Non-approved tools rejected
        assertThat(ToolAllowlist.isAllowed("restart_application")).isFalse();
        assertThat(ToolAllowlist.isAllowed("execute_bash_command")).isFalse();
        assertThat(ToolAllowlist.isAllowed("execute_sql")).isFalse();
        assertThat(ToolAllowlist.isAllowed("run_sql")).isFalse();
        assertThat(ToolAllowlist.isAllowed("query_database")).isFalse();
        assertThat(ToolAllowlist.isAllowed("unknown_tool")).isFalse();
    }
}
