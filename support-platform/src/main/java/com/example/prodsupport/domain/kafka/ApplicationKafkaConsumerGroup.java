package com.example.prodsupport.domain.kafka;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;

@Entity
@Table(
        name = "application_kafka_consumer_group",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_app_kafka_cg", columnNames = {"kafka_config_id", "consumer_group"})
        }
)
public class ApplicationKafkaConsumerGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kafka_config_id", nullable = false)
    @JsonBackReference
    private ApplicationKafkaConfig kafkaConfig;

    @Column(name = "consumer_group", nullable = false, length = 200)
    private String consumerGroup;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    public ApplicationKafkaConsumerGroup() {
    }

    public ApplicationKafkaConsumerGroup(ApplicationKafkaConfig kafkaConfig, String consumerGroup, boolean enabled) {
        this.kafkaConfig = kafkaConfig;
        this.consumerGroup = consumerGroup;
        this.enabled = enabled;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ApplicationKafkaConfig getKafkaConfig() {
        return kafkaConfig;
    }

    public void setKafkaConfig(ApplicationKafkaConfig kafkaConfig) {
        this.kafkaConfig = kafkaConfig;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public void setConsumerGroup(String consumerGroup) {
        this.consumerGroup = consumerGroup;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
