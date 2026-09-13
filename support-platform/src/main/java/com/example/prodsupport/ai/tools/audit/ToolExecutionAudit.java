package com.example.prodsupport.ai.tools.audit;

import java.time.Instant;

public record ToolExecutionAudit(
        String toolName,
        String applicationName,
        String environment,
        Instant startTime,
        Instant endTime,
        long durationMs,
        boolean success,
        String failureReason
) {}
