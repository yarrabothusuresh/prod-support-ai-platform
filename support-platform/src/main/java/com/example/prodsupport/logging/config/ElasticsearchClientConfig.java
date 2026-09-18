package com.example.prodsupport.logging.config;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticsearchClientConfig {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public RestClient elasticsearchRestClient(LoggingProperties properties) {
        String url = properties.getElasticsearchUrl();
        if (url == null || url.isBlank()) {
            url = "http://localhost:9200";
        }
        return RestClient.builder(HttpHost.create(url))
                .setRequestConfigCallback(requestConfigBuilder -> requestConfigBuilder
                        .setConnectTimeout((int) properties.getRequestTimeout().toMillis())
                        .setSocketTimeout((int) properties.getRequestTimeout().toMillis()))
                .build();
    }
}
