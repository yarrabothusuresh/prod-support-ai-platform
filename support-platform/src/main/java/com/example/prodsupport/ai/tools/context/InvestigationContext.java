package com.example.prodsupport.ai.tools.context;

import com.example.prodsupport.ai.tools.model.ToolExecutionResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class InvestigationContext {

    private final String applicationName;
    private final String environment;
    private final String question;
    private final Instant startTime;
    private final int maxToolCalls;

    private final List<String> toolsRequested = Collections.synchronizedList(new ArrayList<>());
    private final Set<String> toolsUsed = Collections.synchronizedSet(new LinkedHashSet<>());
    private final List<ToolExecutionResult<?>> toolResults = Collections.synchronizedList(new ArrayList<>());
    private final List<String> evidence = Collections.synchronizedList(new ArrayList<>());
    private final List<String> warnings = Collections.synchronizedList(new ArrayList<>());
    private final java.util.Map<String, Object> gatheredEvidence = Collections.synchronizedMap(new java.util.LinkedHashMap<>());
    private final AtomicInteger executionCount = new AtomicInteger(0);

    public InvestigationContext(String applicationName, String environment, String question, int maxToolCalls) {
        this.applicationName = applicationName;
        this.environment = environment;
        this.question = question;
        this.maxToolCalls = maxToolCalls > 0 ? maxToolCalls : 6;
        this.startTime = Instant.now();
    }

    public boolean canExecuteTool() {
        return executionCount.get() < maxToolCalls;
    }

    public int incrementExecutionCount() {
        return executionCount.incrementAndGet();
    }

    public int getExecutionCount() {
        return executionCount.get();
    }

    public int getMaxToolCalls() {
        return maxToolCalls;
    }

    public void recordToolRequested(String toolName) {
        if (toolName != null) {
            toolsRequested.add(toolName);
        }
    }

    public void recordToolExecution(ToolExecutionResult<?> result) {
        if (result != null) {
            toolResults.add(result);
            toolsUsed.add(result.toolName());
            if (result.warning() != null && !result.warning().isBlank()) {
                warnings.add(result.warning());
            }
        }
    }

    public void recordWarning(String warning) {
        if (warning != null && !warning.isBlank() && !warnings.contains(warning)) {
            warnings.add(warning);
        }
    }

    public void recordEvidence(String evidenceItem) {
        if (evidenceItem != null && !evidenceItem.isBlank()) {
            evidence.add(evidenceItem);
        }
    }

    public String getApplicationName() {
        return applicationName;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getQuestion() {
        return question;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public List<String> getToolsRequested() {
        return new ArrayList<>(toolsRequested);
    }

    public List<String> getToolsUsed() {
        return new ArrayList<>(toolsUsed);
    }

    public List<ToolExecutionResult<?>> getToolResults() {
        return new ArrayList<>(toolResults);
    }

    public List<String> getEvidence() {
        return new ArrayList<>(evidence);
    }

    public List<String> getWarnings() {
        return new ArrayList<>(warnings);
    }

    public java.util.Map<String, Object> getGatheredEvidence() {
        return Collections.unmodifiableMap(gatheredEvidence);
    }

    public void putEvidence(String key, Object value) {
        if (key != null && value != null) {
            gatheredEvidence.put(key, value);
        }
    }
}
