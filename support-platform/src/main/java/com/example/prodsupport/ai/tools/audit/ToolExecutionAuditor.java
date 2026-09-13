package com.example.prodsupport.ai.tools.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
public class ToolExecutionAuditor {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutionAuditor.class);

    private final ConcurrentLinkedQueue<ToolExecutionAudit> auditHistory = new ConcurrentLinkedQueue<>();
    private static final int MAX_AUDIT_HISTORY = 1000;

    public void audit(ToolExecutionAudit audit) {
        if (audit == null) {
            return;
        }

        auditHistory.add(audit);
        while (auditHistory.size() > MAX_AUDIT_HISTORY) {
            auditHistory.poll();
        }

        // Standardized audit log format required by Day 4 spec:
        // AI_TOOL_EXECUTION tool=... application=... environment=... success=... durationMs=...
        if (audit.success()) {
            log.info("AI_TOOL_EXECUTION tool={} application={} environment={} success=true durationMs={}",
                    audit.toolName(), audit.applicationName(), audit.environment(), audit.durationMs());
        } else {
            log.warn("AI_TOOL_EXECUTION tool={} application={} environment={} success=false durationMs={} reason='{}'",
                    audit.toolName(), audit.applicationName(), audit.environment(), audit.durationMs(), audit.failureReason());
        }
    }

    public List<ToolExecutionAudit> getAuditHistory() {
        return new ArrayList<>(auditHistory);
    }

    public void clearHistory() {
        auditHistory.clear();
    }
}
