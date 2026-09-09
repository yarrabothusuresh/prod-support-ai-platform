package com.example.prodsupport.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    @Bean
    @ConditionalOnMissingBean
    public OllamaApi ollamaApi(AiProperties properties,
                               ObjectMapper objectMapper,
                               ObjectProvider<WebClient.Builder> webClientBuilderProvider) {
        MappingJackson2HttpMessageConverter jacksonConverter = new MappingJackson2HttpMessageConverter(objectMapper);
        jacksonConverter.setSupportedMediaTypes(List.of(
                MediaType.APPLICATION_JSON,
                MediaType.APPLICATION_OCTET_STREAM,
                new MediaType("application", "*+json")
        ));

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeoutSec = properties.getTimeoutSeconds() > 0 ? properties.getTimeoutSeconds() : 60;
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSec));

        RestClient.Builder restClientBuilder = RestClient.builder()
                .requestFactory(requestFactory)
                .messageConverters(converters -> converters.add(0, jacksonConverter));

        WebClient.Builder webClientBuilder = webClientBuilderProvider.getIfAvailable(WebClient::builder);

        return new OllamaApi(properties.getBaseUrl(), restClientBuilder, webClientBuilder);
    }

    @Bean
    public RestClientCustomizer restClientCustomizer(ObjectMapper objectMapper) {
        return restClientBuilder -> {
            MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);
            converter.setSupportedMediaTypes(List.of(
                    MediaType.APPLICATION_JSON,
                    MediaType.APPLICATION_OCTET_STREAM,
                    new MediaType("application", "*+json")
            ));
            restClientBuilder.messageConverters(converters -> converters.add(0, converter));
        };
    }
}
