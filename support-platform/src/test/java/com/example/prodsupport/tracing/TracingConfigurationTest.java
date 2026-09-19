package com.example.prodsupport.tracing;

import com.example.prodsupport.tracing.config.TracingProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TracingConfigurationTest {

    @Autowired
    private TracingProperties tracingProperties;

    @Test
    @DisplayName("Verify tracing properties load correctly with expected defaults")
    void shouldLoadTracingPropertiesWithDefaults() {
        assertThat(tracingProperties).isNotNull();
        assertThat(tracingProperties.isEnabled()).isTrue();
        assertThat(tracingProperties.getJaegerBaseUrl()).isNotBlank();
        assertThat(tracingProperties.getDefaultWindowMinutes()).isEqualTo(15);
        assertThat(tracingProperties.getMaximumWindowMinutes()).isEqualTo(120);
        assertThat(tracingProperties.getDefaultResultLimit()).isEqualTo(20);
        assertThat(tracingProperties.getMaximumResultLimit()).isEqualTo(50);
        assertThat(tracingProperties.getRequestTimeout().toSeconds()).isLessThanOrEqualTo(5);
    }
}
