package com.example.prodsupport.infrastructure.kafka;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "prod-support.kafka")
public class KafkaDiagnosticProperties {

    private boolean enabled = true;
    private List<String> bootstrapServers = new ArrayList<>(List.of("localhost:9092"));
    private Duration requestTimeout = Duration.ofSeconds(5);
    private int maxGroupsPerRequest = 20;
    private int maxPartitionsPerRequest = 100;
    private LagProperties lag = new LagProperties();

    public static class LagProperties {
        private long warningThreshold = 100;
        private long criticalThreshold = 1000;

        public long getWarningThreshold() {
            return warningThreshold;
        }

        public void setWarningThreshold(long warningThreshold) {
            this.warningThreshold = warningThreshold;
        }

        public long getCriticalThreshold() {
            return criticalThreshold;
        }

        public void setCriticalThreshold(long criticalThreshold) {
            this.criticalThreshold = criticalThreshold;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getBootstrapServers() {
        return bootstrapServers;
    }

    public void setBootstrapServers(List<String> bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public int getMaxGroupsPerRequest() {
        return maxGroupsPerRequest;
    }

    public void setMaxGroupsPerRequest(int maxGroupsPerRequest) {
        this.maxGroupsPerRequest = maxGroupsPerRequest;
    }

    public int getMaxPartitionsPerRequest() {
        return maxPartitionsPerRequest;
    }

    public void setMaxPartitionsPerRequest(int maxPartitionsPerRequest) {
        this.maxPartitionsPerRequest = maxPartitionsPerRequest;
    }

    public LagProperties getLag() {
        return lag;
    }

    public void setLag(LagProperties lag) {
        this.lag = lag;
    }
}
