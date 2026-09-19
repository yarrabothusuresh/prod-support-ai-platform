package com.example.prodsupport.tracing.client;

import com.example.prodsupport.tracing.model.TraceDetailResult;
import com.example.prodsupport.tracing.model.TraceSearchResult;

import java.util.Optional;

public interface TraceSearchClient {

    boolean isAvailable();

    TraceSearchResult searchTraces(String serviceName, String environment, int minutes, int limit, boolean errorOnly);

    Optional<TraceDetailResult> getTraceById(String traceId);

    String getProviderName();

    String getEndpointUrl();
}
