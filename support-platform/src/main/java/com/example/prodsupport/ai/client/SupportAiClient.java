package com.example.prodsupport.ai.client;

import com.example.prodsupport.ai.model.ApplicationSupportContext;
import com.example.prodsupport.ai.model.SupportAiResult;

public interface SupportAiClient {

    /**
     * Sends the application support context and user question to the AI model
     * and returns a structured support result.
     *
     * @param context live application metadata and health context
     * @param userQuestion question posed by the support engineer
     * @return structured AI analysis result
     */
    SupportAiResult analyze(ApplicationSupportContext context, String userQuestion);

    /**
     * Checks whether the underlying AI model/service is reachable and ready.
     *
     * @return true if available, false otherwise
     */
    boolean isAvailable();

    /**
     * Gets the configured model name.
     */
    String getModelName();

    /**
     * Gets the provider name (e.g. "ollama").
     */
    String getProviderName();
}
