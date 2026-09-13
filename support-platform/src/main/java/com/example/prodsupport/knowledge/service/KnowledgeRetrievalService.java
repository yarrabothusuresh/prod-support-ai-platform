package com.example.prodsupport.knowledge.service;

import com.example.prodsupport.ai.config.KnowledgeProperties;
import com.example.prodsupport.domain.DocumentType;
import com.example.prodsupport.knowledge.model.KnowledgeEvidence;
import com.example.prodsupport.knowledge.model.KnowledgeSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class KnowledgeRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRetrievalService.class);

    private final VectorStore vectorStore;
    private final KnowledgeProperties properties;

    @Autowired
    public KnowledgeRetrievalService(VectorStore vectorStore, KnowledgeProperties properties) {
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    public List<KnowledgeEvidence> retrieve(String applicationName,
                                           String environment,
                                           String query,
                                           Collection<DocumentType> documentTypes,
                                           Integer topK) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        int effectiveTopK = sanitizeTopK(topK);
        // Query VectorStore with expanded candidate pool to allow post-filtering
        int searchCandidateCount = Math.min(Math.max(effectiveTopK * 3, 15), 50);

        List<Document> rawResults;
        try {
            SearchRequest request = SearchRequest.query(query)
                    .withTopK(searchCandidateCount);
            rawResults = vectorStore.similaritySearch(request);
        } catch (Exception e) {
            log.warn("Vector search failed or vector store empty for query '{}': {}", query, e.getMessage());
            return Collections.emptyList();
        }

        if (rawResults == null || rawResults.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> typeNames = (documentTypes != null && !documentTypes.isEmpty())
                ? documentTypes.stream().map(Enum::name).collect(Collectors.toSet())
                : Collections.emptySet();

        List<KnowledgeEvidence> filtered = new ArrayList<>();
        int accumulatedChars = 0;
        int maxChars = properties.getMaxContextChars() > 0 ? properties.getMaxContextChars() : 12000;

        for (Document doc : rawResults) {
            Map<String, Object> meta = doc.getMetadata();
            if (meta == null) {
                continue;
            }

            String docApp = String.valueOf(meta.getOrDefault("applicationName", ""));
            String docEnv = String.valueOf(meta.getOrDefault("environment", ""));
            String docType = String.valueOf(meta.getOrDefault("documentType", ""));

            // Application and environment strict scoping
            if (applicationName != null && !applicationName.equalsIgnoreCase(docApp)) {
                continue;
            }
            if (environment != null && !environment.equalsIgnoreCase(docEnv)) {
                continue;
            }

            // Document type scoping
            if (!typeNames.isEmpty() && !typeNames.contains(docType)) {
                continue;
            }

            // Extract similarity score if available
            Double score = 1.0;
            if (meta.containsKey("distance")) {
                try {
                    score = 1.0 - Double.parseDouble(String.valueOf(meta.get("distance")));
                } catch (Exception ignored) {
                }
            }

            KnowledgeEvidence evidence = KnowledgeEvidence.fromDocument(doc, score);
            int contentLength = evidence.content() != null ? evidence.content().length() : 0;

            if (accumulatedChars + contentLength > maxChars && !filtered.isEmpty()) {
                log.info("Knowledge retrieval reached max context chars limit ({} chars). Truncating further chunks.", maxChars);
                break;
            }

            filtered.add(evidence);
            accumulatedChars += contentLength;

            if (filtered.size() >= effectiveTopK) {
                break;
            }
        }

        log.debug("Knowledge retrieval for app '{}', env '{}' query '{}': {} raw matches -> {} filtered",
                applicationName, environment, query, rawResults.size(), filtered.size());

        return filtered;
    }

    public List<KnowledgeSource> extractDeduplicatedSources(List<KnowledgeEvidence> evidenceList) {
        if (evidenceList == null || evidenceList.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, KnowledgeSource> uniqueSources = new LinkedHashMap<>();
        for (KnowledgeEvidence evidence : evidenceList) {
            String key = evidence.title() + "::" + evidence.source();
            uniqueSources.putIfAbsent(key, KnowledgeSource.fromEvidence(evidence));
        }

        return new ArrayList<>(uniqueSources.values());
    }

    public int sanitizeTopK(Integer requestedTopK) {
        int defaultK = properties.getDefaultTopK() > 0 ? properties.getDefaultTopK() : 5;
        int maxK = properties.getMaxTopK() > 0 ? properties.getMaxTopK() : 10;
        if (requestedTopK == null || requestedTopK < 1) {
            return defaultK;
        }
        return Math.min(requestedTopK, maxK);
    }
}
