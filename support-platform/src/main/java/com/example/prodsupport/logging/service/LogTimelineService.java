package com.example.prodsupport.logging.service;

import com.example.prodsupport.ai.tools.security.ApplicationAccessValidator;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.logging.client.LogSearchClient;
import com.example.prodsupport.logging.config.LoggingProperties;
import com.example.prodsupport.logging.entity.ApplicationLoggingConfigEntity;
import com.example.prodsupport.logging.model.LogSearchCriteria;
import com.example.prodsupport.logging.model.LogTimelineResult;
import com.example.prodsupport.logging.repository.ApplicationLoggingConfigRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class LogTimelineService {

    private final ApplicationAccessValidator accessValidator;
    private final ApplicationLoggingConfigRepository loggingConfigRepository;
    private final LogSearchClient logSearchClient;
    private final LoggingProperties properties;

    public LogTimelineService(ApplicationAccessValidator accessValidator,
                              ApplicationLoggingConfigRepository loggingConfigRepository,
                              LogSearchClient logSearchClient,
                              LoggingProperties properties) {
        this.accessValidator = accessValidator;
        this.loggingConfigRepository = loggingConfigRepository;
        this.logSearchClient = logSearchClient;
        this.properties = properties;
    }

    public LogTimelineResult getTimeline(String applicationName,
                                         String environment,
                                         Instant startTime,
                                         Instant endTime,
                                         String correlationId,
                                         Integer limit) {
        RegisteredApplication app = accessValidator.validateAndGet(applicationName, environment);

        ApplicationLoggingConfigEntity config = loggingConfigRepository.findByApplication(app).orElse(null);
        if (config != null && !config.isLoggingEnabled()) {
            return new LogTimelineResult(app.getApplicationName(), app.getEnvironment(), List.of(),
                    List.of("Centralized logging is disabled for application '" + app.getApplicationName() + "'"));
        }

        Instant end = endTime != null ? endTime : Instant.now();
        Instant start = startTime != null ? startTime : end.minus(Duration.ofMinutes(properties.getDefaultWindowMinutes()));

        String indexPattern = config != null && config.getIndexPattern() != null && !config.getIndexPattern().isBlank()
                ? config.getIndexPattern()
                : properties.getDefaultIndexPattern();

        LogSearchCriteria criteria = new LogSearchCriteria(
                app.getApplicationName(),
                app.getEnvironment(),
                start,
                end,
                List.of(), // all levels for timeline
                null,
                correlationId,
                limit != null && limit > 0 ? limit : properties.getDefaultResultLimit(),
                indexPattern
        );

        return logSearchClient.getTimeline(criteria);
    }
}
