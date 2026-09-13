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
    private final ObjectMapper objectMapper;

    private final Map<String, FunctionCallback> callbacks = new LinkedHashMap<>();

    public DiagnosticToolRegistry(ApplicationInfoAiTool applicationInfoAiTool,
                                  HealthAiTool healthAiTool,
                                  RecentErrorsAiTool recentErrorsAiTool,
                                  DependencyAiTool dependencyAiTool,
                                  ObjectMapper objectMapper) {
        this.applicationInfoAiTool = applicationInfoAiTool;
        this.healthAiTool = healthAiTool;
        this.recentErrorsAiTool = recentErrorsAiTool;
        this.dependencyAiTool = dependencyAiTool;
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
