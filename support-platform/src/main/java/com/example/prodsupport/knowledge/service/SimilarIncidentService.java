package com.example.prodsupport.knowledge.service;

import com.example.prodsupport.domain.DocumentType;
import com.example.prodsupport.knowledge.model.KnowledgeEvidence;
import com.example.prodsupport.knowledge.model.KnowledgeSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class SimilarIncidentService {

    private static final Logger log = LoggerFactory.getLogger(SimilarIncidentService.class);

    private final KnowledgeRetrievalService retrievalService;

    public SimilarIncidentService(KnowledgeRetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    public SimilarIncidentResult findSimilarIncidents(String applicationName,
                                                      String environment,
                                                      String symptomQuery,
                                                      Integer topK) {
        log.info("Searching historical incidents and RCAs for app '{}' symptom: '{}'", applicationName, symptomQuery);
        List<KnowledgeEvidence> evidence = retrievalService.retrieve(
                applicationName,
                environment,
                symptomQuery,
                Set.of(DocumentType.INCIDENT, DocumentType.RCA),
                topK != null ? topK : 3
        );

        List<KnowledgeSource> sources = retrievalService.extractDeduplicatedSources(evidence);
        String historicalDisclaimer = "IMPORTANT: Historical incidents provide context on past failure modes. " +
                "A historical root cause MUST NOT automatically be assumed to be the cause of any active production incident.";

        return new SimilarIncidentResult(
                applicationName,
                environment,
                symptomQuery,
                evidence,
                sources,
                historicalDisclaimer
        );
    }

    public record SimilarIncidentResult(
            String applicationName,
            String environment,
            String symptomQuery,
            List<KnowledgeEvidence> incidents,
            List<KnowledgeSource> sources,
            String historicalDisclaimer
    ) {}
}
