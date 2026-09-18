package com.example.prodsupport.logging.evidence;

import com.example.prodsupport.logging.model.*;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Component
public class LogEvidenceMapper {

    public String buildBoundedErrorSummary(String appName, String env, int windowMinutes,
                                           LogSearchResult searchResult, ErrorPatternResult patternResult) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== CENTRALIZED LOG EVIDENCE (ELASTICSEARCH) ===\n");
        sb.append("Application: ").append(appName).append("\n");
        sb.append("Environment: ").append(env).append("\n");
        sb.append("Time Window: Last ").append(windowMinutes).append(" minutes\n");

        long totalErrors = searchResult != null ? searchResult.totalHits() : 0;
        sb.append("Total Error Hits: ").append(totalErrors).append("\n");

        if (patternResult != null && !patternResult.patterns().isEmpty()) {
            sb.append("Top Recurring Error Patterns:\n");
            for (ErrorPatternDto pattern : patternResult.patterns()) {
                sb.append("  - ").append(pattern.errorType()).append(": ").append(pattern.count()).append(" occurrences\n");
            }
        }

        if (searchResult != null && !searchResult.logs().isEmpty()) {
            sb.append("Representative Sanitized Error Events:\n");
            int maxSamples = Math.min(5, searchResult.logs().size());
            for (int i = 0; i < maxSamples; i++) {
                LogEntryDto log = searchResult.logs().get(i);
                sb.append("  - [").append(log.timestamp() != null ? log.timestamp() : "N/A").append("] ")
                        .append(log.errorType() != null ? "[" + log.errorType() + "] " : "")
                        .append(log.message() != null ? log.message() : "No message")
                        .append(log.correlationId() != null ? " (corrId=" + log.correlationId() + ")" : "")
                        .append("\n");
            }
            if (searchResult.truncated() || searchResult.totalHits() > maxSamples) {
                sb.append("  [NOTICE: Evidence contains representative samples from ").append(searchResult.totalHits())
                        .append(" total recorded events]\n");
            }
        } else {
            sb.append("No matching error logs found in Elasticsearch within the query window.\n");
        }

        List<String> warnings = new ArrayList<>();
        if (searchResult != null && searchResult.warnings() != null) {
            warnings.addAll(searchResult.warnings());
        }
        if (patternResult != null && patternResult.warnings() != null) {
            warnings.addAll(patternResult.warnings());
        }

        if (!warnings.isEmpty()) {
            sb.append("Log Search Warnings:\n");
            for (String w : warnings) {
                sb.append("  - ").append(w).append("\n");
            }
        }

        return sb.toString();
    }

    public Map<String, Object> toEvidenceItem(String type, String appName, String env, String summary) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("source", "ELASTICSEARCH");
        map.put("type", type);
        map.put("applicationName", appName);
        map.put("environment", env);
        map.put("summary", summary);
        map.put("collectedAt", Instant.now().toString());
        return map;
    }
}
