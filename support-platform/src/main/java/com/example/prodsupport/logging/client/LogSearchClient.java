package com.example.prodsupport.logging.client;

import com.example.prodsupport.logging.model.ErrorPatternResult;
import com.example.prodsupport.logging.model.LogSearchCriteria;
import com.example.prodsupport.logging.model.LogSearchResult;
import com.example.prodsupport.logging.model.LogTimelineResult;

public interface LogSearchClient {

    LogSearchResult search(LogSearchCriteria criteria);

    ErrorPatternResult summarizeErrors(LogSearchCriteria criteria);

    LogTimelineResult getTimeline(LogSearchCriteria criteria);

    boolean isAvailable();
}
