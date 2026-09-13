package com.example.prodsupport.knowledge.prompt;

import com.example.prodsupport.knowledge.model.KnowledgeEvidence;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class KnowledgePromptBuilder {

    public String buildKnowledgeSystemPrompt() {
        return """
                You are an expert production support engineer assisting on-call personnel by retrieving and explaining operational knowledge.
                
                CRITICAL INSTRUCTIONS & ANTI-HALLUCINATION RULES:
                1. Ground your response STRICTLY in the provided Documentation & Runbook Excerpts.
                2. DO NOT invent, hallucinate, or assume procedures, commands, or architecture details not present in the excerpts.
                3. If the provided excerpts DO NOT contain enough information to answer the question, clearly state: "The current knowledge base does not contain enough documented information to answer this question."
                4. When referring to guidance or procedures, cite the specific document title.
                5. HISTORICAL INCIDENT DISCLAIMER: Historical incident postmortems (INCIDENT/RCA) represent past events. A historical root cause must NEVER automatically be assumed to be the current root cause.
                6. Differentiate clearly between documented recommendations vs. speculative advice.
                """;
    }

    public String buildKnowledgeUserPrompt(String applicationName,
                                           String environment,
                                           String userQuestion,
                                           List<KnowledgeEvidence> evidenceList) {
        StringBuilder sb = new StringBuilder();
        sb.append("APPLICATION UNDER INQUIRY: ").append(applicationName).append("\n");
        sb.append("ENVIRONMENT: ").append(environment).append("\n\n");
        sb.append("USER QUESTION:\n").append(userQuestion).append("\n\n");

        sb.append("=== RETRIEVED KNOWLEDGE BASE EXCERPTS ===\n");
        if (evidenceList == null || evidenceList.isEmpty()) {
            sb.append("(No relevant documentation found in the knowledge base for this query)\n\n");
        } else {
            for (int i = 0; i < evidenceList.size(); i++) {
                KnowledgeEvidence ev = evidenceList.get(i);
                sb.append(String.format("--- Excerpt #%d [%s] \"%s\" (Source: %s) ---\n",
                        i + 1, ev.documentType(), ev.title(), ev.source()));
                sb.append(ev.content()).append("\n\n");
            }
        }

        sb.append("INSTRUCTIONS FOR ANSWER:\n");
        sb.append("- Provide a clear, actionable summary answering the user's question based on the excerpts.\n");
        sb.append("- Mention the titles of the documents from which the guidance is derived.\n");
        sb.append("- If historical incident postmortems are mentioned, explicitly state that past causes may not reflect the active issue.\n");

        return sb.toString();
    }
}
