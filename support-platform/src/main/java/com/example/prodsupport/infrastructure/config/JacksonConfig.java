package com.example.prodsupport.infrastructure.config;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> builder.featuresToEnable(
                JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES.mappedFeature(),
                JsonReadFeature.ALLOW_SINGLE_QUOTES.mappedFeature()
        );
    }
}
