package com.example.prodsupport.logging.service;

import com.example.prodsupport.ai.tools.security.ApplicationAccessValidator;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.logging.client.LogSearchClient;
import com.example.prodsupport.logging.config.LoggingProperties;
import com.example.prodsupport.logging.entity.ApplicationLoggingConfigEntity;
import com.example.prodsupport.logging.model.LogSearchCriteria;
import com.example.prodsupport.logging.model.LogSearchResult;
import com.example.prodsupport.logging.repository.ApplicationLoggingConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class LogSearchService {

    private static final Logger log = LoggerFactory.getLogger(LogSearchService.class);

    private final ApplicationAccessValidator accessValidator;
    private final ApplicationLoggingConfigRepository loggingConfigRepository;
    private final LogSearchClient logSearchClient;
    private final LoggingProperties properties;

    public LogSearchService(ApplicationAccessValidator accessValidator,
                            ApplicationLoggingConfigRepository loggingConfigRepository,
                            LogSearchClient logSearchClient,
                            LoggingProperties properties) {
        this.accessValidator = accessValidator;
        this.loggingConfigRepository = loggingConfigRepository;
        this.logSearchClient = logSearchClient;
        this.properties = properties;
    }

    public LogSearchResult searchLogs(String applicationName,
                                      String environment,
                                      Instant startTime,
                                      Instant endTime,
                                      List<String> levels,
                                      String keyword,
                                      String correlationId,
                                      Integer limit) {
        RegisteredApplication app = accessValidator.validateAndGet(applicationName, environment);

        ApplicationLoggingConfigEntity config = loggingConfigRepository.findByApplication(app)
                .orElse(null);

        if (config != null && !config.isLoggingEnabled()) {
            return new LogSearchResult(app.getApplicationName(), app.getEnvironment(), 0, false, List.of(),
                    List.of("Centralized logging is disabled for application '" + app.getApplicationName() + "'"));
        }

        String indexPattern = config != null && config.getIndexPattern() != null && !config.getIndexPattern().isBlank()
                ? config.getIndexPattern()
                : properties.getDefaultIndexPattern();

        LogSearchCriteria criteria = new LogSearchCriteria(
                app.getApplicationName(),
                app.getEnvironment(),
                startTime,
                endTime,
                levels,
                keyword,
                correlationId,
                limit,
                indexPattern
        );

        return logSearchClient.search(criteria);
    }

    public LogSearchResult searchLogs(String applicationName,
                                      String environment,
                                      Instant startTime,
                                      Instant endTime,
                                      List<String> levels,
                                      String keyword,
                                      String correlationId,
                                      String traceId,
                                      Integer limit) {
        RegisteredApplication app = accessValidator.validateAndGet(applicationName, environment);

        ApplicationLoggingConfigEntity config = loggingConfigRepository.findByApplication(app)
                .orElse(null);

        if (config != null && !config.isLoggingEnabled()) {
            return new LogSearchResult(app.getApplicationName(), app.getEnvironment(), 0, false, List.of(),
                    List.of("Centralized logging is disabled for application '" + app.getApplicationName() + "'"));
        }

        String indexPattern = config != null && config.getIndexPattern() != null && !config.getIndexPattern().isBlank()
                ? config.getIndexPattern()
                : properties.getDefaultIndexPattern();

        LogSearchCriteria criteria = new LogSearchCriteria(
                app.getApplicationName(),
                app.getEnvironment(),
                startTime,
                endTime,
                levels,
                keyword,
                correlationId,
                traceId,
                limit,
                indexPattern
        );

        return logSearchClient.search(criteria);
    }
}
