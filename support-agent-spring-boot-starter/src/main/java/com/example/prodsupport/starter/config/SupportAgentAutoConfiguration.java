package com.example.prodsupport.starter.config;

import com.example.prodsupport.starter.controller.SupportInfoController;
import com.example.prodsupport.starter.properties.SupportProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "prod-support", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(SupportProperties.class)
public class SupportAgentAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SupportInfoController supportInfoController(SupportProperties properties,
                                                       ObjectProvider<HealthEndpoint> healthEndpointProvider) {
        return new SupportInfoController(properties, healthEndpointProvider);
    }
}
