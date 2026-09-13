package com.example.prodsupport.ai.tools.model;

import java.time.Instant;

public record ToolExecutionResult<T>(
        String toolName,
        boolean success,
        T data,
        String warning,
        Instant executedAt,
        long durationMs
) {
    public static <T> ToolExecutionResult<T> success(String toolName, T data, Instant executedAt, long durationMs) {
        return new ToolExecutionResult<>(toolName, true, data, null, executedAt, durationMs);
    }

    public static <T> ToolExecutionResult<T> failure(String toolName, String warning, Instant executedAt, long durationMs) {
        return new ToolExecutionResult<>(toolName, false, null, warning, executedAt, durationMs);
    }
}
