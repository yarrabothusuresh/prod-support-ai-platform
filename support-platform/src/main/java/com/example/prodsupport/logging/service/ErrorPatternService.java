package com.example.prodsupport.logging.service;

import com.example.prodsupport.ai.tools.security.ApplicationAccessValidator;
import com.example.prodsupport.domain.RegisteredApplication;
import com.example.prodsupport.logging.client.LogSearchClient;
import com.example.prodsupport.logging.config.LoggingProperties;
import com.example.prodsupport.logging.entity.ApplicationLoggingConfigEntity;
import com.example.prodsupport.logging.model.ErrorPatternResult;
import com.example.prodsupport.logging.model.LogSearchCriteria;
import com.example.prodsupport.logging.repository.ApplicationLoggingConfigRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class ErrorPatternService {

    private final ApplicationAccessValidator accessValidator;
    private final ApplicationLoggingConfigRepository loggingConfigRepository;
    private final LogSearchClient logSearchClient;
    private final LoggingProperties properties;

    public ErrorPatternService(ApplicationAccessValidator accessValidator,
                               ApplicationLoggingConfigRepository loggingConfigRepository,
                               LogSearchClient logSearchClient,
                               LoggingProperties properties) {
        this.accessValidator = accessValidator;
        this.loggingConfigRepository = loggingConfigRepository;
        this.logSearchClient = logSearchClient;
        this.properties = properties;
    }

    public ErrorPatternResult summarizeErrors(String applicationName,
                                              String environment,
                                              Integer minutes,
                                              Integer limit) {
        RegisteredApplication app = accessValidator.validateAndGet(applicationName, environment);

        ApplicationLoggingConfigEntity config = loggingConfigRepository.findByApplication(app).orElse(null);
        if (config != null && !config.isLoggingEnabled()) {
            return new ErrorPatternResult(app.getApplicationName(), app.getEnvironment(),
                    minutes != null ? minutes : properties.getDefaultWindowMinutes(), List.of(),
                    List.of("Centralized logging is disabled for application '" + app.getApplicationName() + "'"));
        }

        int windowMinutes = minutes != null && minutes > 0 ? minutes : properties.getDefaultWindowMinutes();
        Instant end = Instant.now();
        Instant start = end.minus(Duration.ofMinutes(windowMinutes));

        String indexPattern = config != null && config.getIndexPattern() != null && !config.getIndexPattern().isBlank()
                ? config.getIndexPattern()
                : properties.getDefaultIndexPattern();

        LogSearchCriteria criteria = new LogSearchCriteria(
                app.getApplicationName(),
                app.getEnvironment(),
                start,
                end,
                List.of("ERROR", "WARN"),
                null,
                null,
                limit != null && limit > 0 ? limit : 10,
                indexPattern
        );

        return logSearchClient.summarizeErrors(criteria);
    }
}
