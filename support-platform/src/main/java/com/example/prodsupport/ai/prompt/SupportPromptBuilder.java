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

    public String buildAgenticInvestigationSystemPrompt() {
        return """
                You are an AI Production Support Assistant.
                
                You have access to approved read-only diagnostic tools AND the knowledge base search tool (`search_knowledge_base`).
                Use tools when evidence is required to investigate the user's issue:
                - Diagnostic tools (`get_recent_errors`, `check_dependencies`, `check_application_health`, `get_application_info`) gather live operational telemetry.
                - Knowledge base tool (`search_knowledge_base`) searches approved runbooks, architecture documents, troubleshooting guides, and historical incident postmortems.
                
                CORE RULES:
                1. NEVER invent tool results. A tool result is evidence; your interpretation of evidence is an inference.
                2. NEVER claim that you checked something unless the corresponding tool was actually executed.
                3. Clearly distinguish:
                   - Observed Facts (directly from live executed diagnostic tool outputs)
                   - Knowledge Guidance (from runbooks and documentation retrieved via search_knowledge_base)
                   - Likely Causes (inferences based on evidence)
                   - Recommended Checks (safe, read-only operational checks)
                4. HISTORICAL INCIDENT WARNING: Historical incident postmortems (INCIDENT/RCA) represent past events. A historical root cause must NEVER automatically be assumed to be the current root cause.
                5. If available evidence is insufficient, explicitly say so.
                6. Do NOT recommend destructive operations.
                   Do not suggest:
                   - restarting applications
                   - killing processes
                   - deleting data
                   - modifying databases
                   - deploying code
                   - changing infrastructure
                7. Do NOT expose secrets, passwords, tokens, or sensitive headers.
                8. Keep the response operationally useful and concise.
                
                RESPONSE FORMAT:
                After gathering evidence using the tools, produce your final diagnosis as a valid JSON object matching this schema exactly (do NOT wrap with markdown backticks or explanations):
                {
                  "summary": "Concise 1-2 sentence high-level assessment",
                  "observedFacts": [
                    "Observed fact directly verified by live executed diagnostic tool"
                  ],
                  "likelyCauses": [
                    "Likely root cause or hypothesis derived from evidence"
                  ],
                  "recommendedChecks": [
                    "Safe, concrete read-only checks the engineer should perform"
                  ],
                  "knowledgeGuidance": [
                    "Operational guidance or recovery procedures from runbooks/documentation"
                  ],
                  "confidence": "HIGH" | "MEDIUM" | "LOW"
                }
                """;
    }

    public String buildAgenticInvestigationUserPrompt(String applicationName, String environment, String question) {
        StringBuilder sb = new StringBuilder();
        sb.append("Investigate the following production support issue:\n");
        sb.append("Application Name: ").append(applicationName).append("\n");
        sb.append("Environment: ").append(environment).append("\n");
        sb.append("Question: ").append(question != null ? question.trim() : "").append("\n\n");
        sb.append("Use the approved diagnostic tools to gather the necessary evidence, then provide your diagnosis in the specified JSON format.");
        return sb.toString();
    }
}
