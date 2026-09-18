package com.example.prodsupport.ai.tools.security;

import java.util.Set;

public final class ToolAllowlist {

    public static final String TOOL_GET_APPLICATION_INFO = "get_application_info";
    public static final String TOOL_CHECK_APPLICATION_HEALTH = "check_application_health";
    public static final String TOOL_GET_RECENT_ERRORS = "get_recent_errors";
    public static final String TOOL_CHECK_DEPENDENCIES = "check_dependencies";
    public static final String TOOL_SEARCH_KNOWLEDGE_BASE = "search_knowledge_base";

    // Kafka Diagnostic Tools (Safe Read-Only)
    public static final String TOOL_CHECK_KAFKA_CLUSTER = "check_kafka_cluster";
    public static final String TOOL_CHECK_KAFKA_CONSUMER_GROUP = "check_kafka_consumer_group";
    public static final String TOOL_CHECK_KAFKA_CONSUMER_LAG = "check_kafka_consumer_lag";
    public static final String TOOL_GET_KAFKA_TOPIC_INFO = "get_kafka_topic_info";

    // Database Diagnostic Tools (Safe Read-Only)
    public static final String TOOL_CHECK_DATABASE_HEALTH = "check_database_health";
    public static final String TOOL_CHECK_DATABASE_CONNECTION_POOL = "check_database_connection_pool";
    public static final String TOOL_CHECK_DATABASE_ACTIVITY = "check_database_activity";

    // Centralized Logging Diagnostic Tools (Safe Read-Only Elasticsearch)
    public static final String TOOL_SEARCH_APPLICATION_ERRORS = "search_application_errors";
    public static final String TOOL_GET_ERROR_PATTERN_SUMMARY = "get_error_pattern_summary";
    public static final String TOOL_GET_APPLICATION_LOG_TIMELINE = "get_application_log_timeline";

    public static final Set<String> APPROVED_TOOLS = Set.of(
            TOOL_GET_APPLICATION_INFO,
            TOOL_CHECK_APPLICATION_HEALTH,
            TOOL_GET_RECENT_ERRORS,
            TOOL_CHECK_DEPENDENCIES,
            TOOL_SEARCH_KNOWLEDGE_BASE,
            TOOL_CHECK_KAFKA_CLUSTER,
            TOOL_CHECK_KAFKA_CONSUMER_GROUP,
            TOOL_CHECK_KAFKA_CONSUMER_LAG,
            TOOL_GET_KAFKA_TOPIC_INFO,
            TOOL_CHECK_DATABASE_HEALTH,
            TOOL_CHECK_DATABASE_CONNECTION_POOL,
            TOOL_CHECK_DATABASE_ACTIVITY,
            TOOL_SEARCH_APPLICATION_ERRORS,
            TOOL_GET_ERROR_PATTERN_SUMMARY,
            TOOL_GET_APPLICATION_LOG_TIMELINE
    );

    private ToolAllowlist() {}

    public static boolean isAllowed(String toolName) {
        return toolName != null && APPROVED_TOOLS.contains(toolName);
    }

}

