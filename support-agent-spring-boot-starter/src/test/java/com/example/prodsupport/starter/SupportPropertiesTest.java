package com.example.prodsupport.starter;

import com.example.prodsupport.starter.properties.SupportProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SupportPropertiesTest {

    @Test
    void shouldBindSupportPropertiesCorrectly() {
        Map<String, Object> map = new HashMap<>();
        map.put("prod-support.enabled", "true");
        map.put("prod-support.application.name", "payment-service");
        map.put("prod-support.application.team", "payments");
        map.put("prod-support.application.description", "Demo payment processing service");
        map.put("prod-support.application.environment", "local");
        map.put("prod-support.diagnostics.health", "true");
        map.put("prod-support.diagnostics.metrics", "false");

        ConfigurationPropertySource source = new MapConfigurationPropertySource(map);
        Binder binder = new Binder(source);

        SupportProperties properties = binder.bind("prod-support", SupportProperties.class).get();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getApplication().getName()).isEqualTo("payment-service");
        assertThat(properties.getApplication().getTeam()).isEqualTo("payments");
        assertThat(properties.getApplication().getDescription()).isEqualTo("Demo payment processing service");
        assertThat(properties.getApplication().getEnvironment()).isEqualTo("local");
        assertThat(properties.getDiagnostics().isHealth()).isTrue();
        assertThat(properties.getDiagnostics().isMetrics()).isFalse();
    }

    @Test
    void shouldHaveDefaultValuesWhenUnconfigured() {
        SupportProperties properties = new SupportProperties();
        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getDiagnostics().isHealth()).isTrue();
        assertThat(properties.getDiagnostics().isMetrics()).isTrue();
        assertThat(properties.getApplication().getName()).isNull();
    }
}
