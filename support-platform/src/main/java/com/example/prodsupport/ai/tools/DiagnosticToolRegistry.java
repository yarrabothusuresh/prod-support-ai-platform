package com.example.prodsupport.ai.tools;

import com.example.prodsupport.ai.tools.model.*;
import com.example.prodsupport.ai.tools.security.ToolAllowlist;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackWrapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class DiagnosticToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticToolRegistry.class);

    private final ApplicationInfoAiTool applicationInfoAiTool;
    private final HealthAiTool healthAiTool;
    private final RecentErrorsAiTool recentErrorsAiTool;
    private final DependencyAiTool dependencyAiTool;
    private final KnowledgeBaseAiTool knowledgeBaseAiTool;
    private final KafkaClusterAiTool kafkaClusterAiTool;
    private final KafkaConsumerGroupAiTool kafkaConsumerGroupAiTool;
    private final KafkaConsumerLagAiTool kafkaConsumerLagAiTool;
    private final KafkaTopicAiTool kafkaTopicAiTool;
    private final DatabaseHealthAiTool databaseHealthAiTool;
    private final DatabaseConnectionPoolAiTool databaseConnectionPoolAiTool;
    private final DatabaseActivityAiTool databaseActivityAiTool;
    private final ObjectMapper objectMapper;

    private final Map<String, FunctionCallback> callbacks = new LinkedHashMap<>();

    public DiagnosticToolRegistry(ApplicationInfoAiTool applicationInfoAiTool,
                                  HealthAiTool healthAiTool,
                                  RecentErrorsAiTool recentErrorsAiTool,
                                  DependencyAiTool dependencyAiTool,
                                  KnowledgeBaseAiTool knowledgeBaseAiTool,
                                  KafkaClusterAiTool kafkaClusterAiTool,
                                  KafkaConsumerGroupAiTool kafkaConsumerGroupAiTool,
                                  KafkaConsumerLagAiTool kafkaConsumerLagAiTool,
                                  KafkaTopicAiTool kafkaTopicAiTool,
                                  DatabaseHealthAiTool databaseHealthAiTool,
                                  DatabaseConnectionPoolAiTool databaseConnectionPoolAiTool,
                                  DatabaseActivityAiTool databaseActivityAiTool,
                                  ObjectMapper objectMapper) {
        this.applicationInfoAiTool = applicationInfoAiTool;
        this.healthAiTool = healthAiTool;
        this.recentErrorsAiTool = recentErrorsAiTool;
        this.dependencyAiTool = dependencyAiTool;
        this.knowledgeBaseAiTool = knowledgeBaseAiTool;
        this.kafkaClusterAiTool = kafkaClusterAiTool;
        this.kafkaConsumerGroupAiTool = kafkaConsumerGroupAiTool;
        this.kafkaConsumerLagAiTool = kafkaConsumerLagAiTool;
        this.kafkaTopicAiTool = kafkaTopicAiTool;
        this.databaseHealthAiTool = databaseHealthAiTool;
        this.databaseConnectionPoolAiTool = databaseConnectionPoolAiTool;
        this.databaseActivityAiTool = databaseActivityAiTool;
        this.objectMapper = objectMapper;

        initCallbacks();
    }


    private void initCallbacks() {
        log.info("Registering safe read-only AI diagnostic tools with Spring AI...");

        // 1. get_application_info
        FunctionCallback appInfoCallback = FunctionCallbackWrapper.builder(applicationInfoAiTool)
                .withName(ApplicationInfoAiTool.TOOL_NAME)
                .withDescription(ApplicationInfoAiTool.TOOL_DESCRIPTION)
                .withInputType(ApplicationInfoRequest.class)
                .withObjectMapper(objectMapper)
                .build();
        callbacks.put(ApplicationInfoAiTool.TOOL_NAME, appInfoCallback);

        // 2. check_application_health
        FunctionCallback healthCallback = FunctionCallbackWrapper.builder(healthAiTool)
                .withName(HealthAiTool.TOOL_NAME)
                .withDescription(HealthAiTool.TOOL_DESCRIPTION)
                .withInputType(ApplicationHealthRequest.class)
                .withObjectMapper(objectMapper)
                .build();
        callbacks.put(HealthAiTool.TOOL_NAME, healthCallback);

        // 3. get_recent_errors
        FunctionCallback errorsCallback = FunctionCallbackWrapper.builder(recentErrorsAiTool)
                .withName(RecentErrorsAiTool.TOOL_NAME)
                .withDescription(RecentErrorsAiTool.TOOL_DESCRIPTION)
                .withInputType(RecentErrorsRequest.class)
                .withObjectMapper(objectMapper)
                .build();
        callbacks.put(RecentErrorsAiTool.TOOL_NAME, errorsCallback);

        // 4. check_dependencies
        FunctionCallback depsCallback = FunctionCallbackWrapper.builder(dependencyAiTool)
                .withName(DependencyAiTool.TOOL_NAME)
                .withDescription(DependencyAiTool.TOOL_DESCRIPTION)
                .withInputType(DependenciesRequest.class)
                .withObjectMapper(objectMapper)
                .build();
        callbacks.put(DependencyAiTool.TOOL_NAME, depsCallback);

        // 5. search_knowledge_base
        FunctionCallback knowledgeCallback = FunctionCallbackWrapper.builder(knowledgeBaseAiTool)
                .withName(KnowledgeBaseAiTool.TOOL_NAME)
                .withDescription(KnowledgeBaseAiTool.TOOL_DESCRIPTION)
                .withInputType(KnowledgeSearchToolRequest.class)
                .withObjectMapper(objectMapper)
                .build();
        callbacks.put(KnowledgeBaseAiTool.TOOL_NAME, knowledgeCallback);

        // 6. check_kafka_cluster
        FunctionCallback kafkaClusterCallback = FunctionCallbackWrapper.builder(kafkaClusterAiTool)
                .withName(KafkaClusterAiTool.TOOL_NAME)
                .withDescription(KafkaClusterAiTool.TOOL_DESCRIPTION)
                .withInputType(KafkaClusterRequest.class)
                .withObjectMapper(objectMapper)
                .build();
        callbacks.put(KafkaClusterAiTool.TOOL_NAME, kafkaClusterCallback);

        // 7. check_kafka_consumer_group
        FunctionCallback kafkaConsumerGroupCallback = FunctionCallbackWrapper.builder(kafkaConsumerGroupAiTool)
                .withName(KafkaConsumerGroupAiTool.TOOL_NAME)
                .withDescription(KafkaConsumerGroupAiTool.TOOL_DESCRIPTION)
                .withInputType(KafkaConsumerGroupRequest.class)
                .withObjectMapper(objectMapper)
                .build();
        callbacks.put(KafkaConsumerGroupAiTool.TOOL_NAME, kafkaConsumerGroupCallback);

        // 8. check_kafka_consumer_lag
        FunctionCallback kafkaConsumerLagCallback = FunctionCallbackWrapper.builder(kafkaConsumerLagAiTool)
                .withName(KafkaConsumerLagAiTool.TOOL_NAME)
                .withDescription(KafkaConsumerLagAiTool.TOOL_DESCRIPTION)
                .withInputType(KafkaConsumerLagRequest.class)
                .withObjectMapper(objectMapper)
                .build();
        callbacks.put(KafkaConsumerLagAiTool.TOOL_NAME, kafkaConsumerLagCallback);

        // 9. get_kafka_topic_info
        FunctionCallback kafkaTopicCallback = FunctionCallbackWrapper.builder(kafkaTopicAiTool)
                .withName(KafkaTopicAiTool.TOOL_NAME)
                .withDescription(KafkaTopicAiTool.TOOL_DESCRIPTION)
                .withInputType(KafkaTopicRequest.class)
                .withObjectMapper(objectMapper)
                .build();
        callbacks.put(KafkaTopicAiTool.TOOL_NAME, kafkaTopicCallback);

        // 10. check_database_health
        FunctionCallback dbHealthCallback = FunctionCallbackWrapper.builder(databaseHealthAiTool)
                .withName(DatabaseHealthAiTool.TOOL_NAME)
                .withDescription(DatabaseHealthAiTool.TOOL_DESCRIPTION)
                .withInputType(DatabaseDiagnosticToolRequest.class)
                .withObjectMapper(objectMapper)
                .build();
        callbacks.put(DatabaseHealthAiTool.TOOL_NAME, dbHealthCallback);

        // 11. check_database_connection_pool
        FunctionCallback dbPoolCallback = FunctionCallbackWrapper.builder(databaseConnectionPoolAiTool)
                .withName(DatabaseConnectionPoolAiTool.TOOL_NAME)
                .withDescription(DatabaseConnectionPoolAiTool.TOOL_DESCRIPTION)
                .withInputType(DatabaseDiagnosticToolRequest.class)
                .withObjectMapper(objectMapper)
                .build();
        callbacks.put(DatabaseConnectionPoolAiTool.TOOL_NAME, dbPoolCallback);

        // 12. check_database_activity
        FunctionCallback dbActivityCallback = FunctionCallbackWrapper.builder(databaseActivityAiTool)
                .withName(DatabaseActivityAiTool.TOOL_NAME)
                .withDescription(DatabaseActivityAiTool.TOOL_DESCRIPTION)
                .withInputType(DatabaseDiagnosticToolRequest.class)
                .withObjectMapper(objectMapper)
                .build();
        callbacks.put(DatabaseActivityAiTool.TOOL_NAME, dbActivityCallback);

        log.info("Successfully registered {} diagnostic tools: {}", callbacks.size(), callbacks.keySet());
    }


    public List<FunctionCallback> getAllCallbacks() {
        return Collections.unmodifiableList(new ArrayList<>(callbacks.values()));
    }

    public FunctionCallback getCallback(String toolName) {
        return callbacks.get(toolName);
    }

    public Set<String> getRegisteredToolNames() {
        return Collections.unmodifiableSet(callbacks.keySet());
    }

    public boolean isRegistered(String toolName) {
        return callbacks.containsKey(toolName);
    }
}
