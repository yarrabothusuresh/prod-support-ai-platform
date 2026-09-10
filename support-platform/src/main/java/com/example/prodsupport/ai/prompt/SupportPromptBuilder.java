package com.example.prodsupport.ai.prompt;

import com.example.prodsupport.ai.model.ApplicationSupportContext;
import com.example.prodsupport.ai.model.SupportDependencyDto;
import com.example.prodsupport.ai.model.SupportErrorDto;
import org.springframework.stereotype.Component;

@Component
public class SupportPromptBuilder {

    public String buildSystemPrompt() {
        return """
                You are an AI production support assistant for an enterprise production support platform.
                Your job is to analyze ONLY the evidence provided in the application context.
                
                CORE RULES:
                1. FACT != INFERENCE: Clearly distinguish between observed facts and potential inferences or hypotheses.
                2. ZERO HALLUCINATION: Do NOT invent production facts, metrics, logs, or dependency states that are not in the evidence.
                3. UNCONFIRMED ROOT CAUSES: Never claim a root cause is confirmed unless explicit evidence proves it. If evidence is insufficient, explicitly state that evidence is insufficient.
                4. NO DESTRUCTIVE ACTIONS: Do NOT suggest destructive production actions such as deleting data, restarting production infrastructure, modifying production databases, or deploying unverified code.
                5. CONCISE & OPERATIONALLY USEFUL: Keep answers concise, factual, and helpful for on-call support engineers.
                
                RESPONSE FORMAT:
                You MUST return a valid, well-formed JSON object matching this schema exactly (do NOT wrap with markdown backticks or explanations):
                {
                  "summary": "Concise 1-2 sentence high-level assessment",
                  "observedFacts": [
                    "Directly verified facts from the context"
                  ],
                  "possibleCauses": [
                    "Potential hypotheses or causes considering the symptoms"
                  ],
                  "recommendedChecks": [
                    "Concrete, safe read-only checks the support engineer should perform"
                  ],
                  "confidence": "HIGH" | "MEDIUM" | "LOW"
                }
                """;
    }

    public String buildUserPrompt(ApplicationSupportContext context, String userQuestion) {
        StringBuilder sb = new StringBuilder();

        sb.append("=== APPLICATION CONTEXT ===\n");
        sb.append("Application Name: ").append(context.applicationName()).append("\n");
        sb.append("Environment: ").append(context.environment()).append("\n");
        sb.append("Team: ").append(context.team() != null ? context.team() : "Unknown").append("\n");
        sb.append("Description: ").append(context.description() != null ? context.description() : "None").append("\n");
        sb.append("Base URL: ").append(context.baseUrl() != null ? context.baseUrl() : "Unknown").append("\n");
        sb.append("\n");

        sb.append("=== LIVE HEALTH & TELEMETRY EVIDENCE ===\n");
        sb.append("Support Info Status: ").append(context.supportStatus())
                .append(context.supportInfoAvailable() ? " (Endpoint Reachable)" : " (Endpoint Unreachable)").append("\n");
        sb.append("Actuator Health Status: ").append(context.actuatorStatus())
                .append(context.healthAvailable() ? " (Endpoint Reachable)" : " (Endpoint Unreachable)").append("\n");
        sb.append("\n");

        sb.append("=== RECENT APPLICATION ERRORS ===\n");
        if (!context.errorsAvailable()) {
            sb.append("Unavailable (Endpoint unreachable or errors diagnostics disabled)\n");
        } else if (context.recentErrors().isEmpty()) {
            sb.append("No recent errors recorded (Clean execution window)\n");
        } else {
            for (SupportErrorDto error : context.recentErrors()) {
                sb.append("- [").append(error.timestamp() != null ? error.timestamp() : "N/A").append("] ")
                        .append("[").append(error.level() != null ? error.level() : "ERROR").append("] ")
                        .append(error.type() != null ? error.type() : "Exception").append(": ")
                        .append(error.message() != null ? error.message() : "No message").append("\n");
            }
        }
        sb.append("\n");

        sb.append("=== DOWNSTREAM DEPENDENCY HEALTH ===\n");
        if (!context.dependenciesAvailable()) {
            sb.append("Unavailable (Endpoint unreachable or dependencies diagnostics disabled)\n");
        } else if (context.dependencies().isEmpty()) {
            sb.append("No downstream dependencies configured\n");
        } else {
            for (SupportDependencyDto dep : context.dependencies()) {
                sb.append("- ").append(dep.name()).append(" (Type: ").append(dep.type()).append("): ")
                        .append("status=").append(dep.status()).append("\n");
            }
        }
        sb.append("\n");

        sb.append("=== CONTEXT WARNINGS ===\n");
        if (context.warnings().isEmpty()) {
            sb.append("None (All telemetry endpoints responded normally)\n");
        } else {
            for (String warning : context.warnings()) {
                sb.append("- WARNING: ").append(warning).append("\n");
            }
        }
        sb.append("\n");

        sb.append("=== USER QUESTION ===\n");
        sb.append(userQuestion != null ? userQuestion.trim() : "").append("\n");
        sb.append("\n");

        sb.append("Remember: Output ONLY valid JSON matching the specified schema.");

        return sb.toString();
    }
}
