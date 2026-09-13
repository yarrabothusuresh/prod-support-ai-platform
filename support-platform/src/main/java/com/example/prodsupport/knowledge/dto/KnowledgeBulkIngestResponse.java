package com.example.prodsupport.knowledge.dto;

import java.util.List;

public record KnowledgeBulkIngestResponse(
        int documentsFound,
        int indexed,
        int skipped,
        int failed,
        List<String> details
) {}
