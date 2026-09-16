package com.example.prodsupport.domain.kafka;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;

@Entity
@Table(
        name = "application_kafka_topic",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_app_kafka_topic", columnNames = {"kafka_config_id", "topic_name"})
        }
)
public class ApplicationKafkaTopic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kafka_config_id", nullable = false)
    @JsonBackReference
    private ApplicationKafkaConfig kafkaConfig;

    @Column(name = "topic_name", nullable = false, length = 200)
    private String topicName;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    public ApplicationKafkaTopic() {
    }

    public ApplicationKafkaTopic(ApplicationKafkaConfig kafkaConfig, String topicName, boolean enabled) {
        this.kafkaConfig = kafkaConfig;
        this.topicName = topicName;
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

    public String getTopicName() {
        return topicName;
    }

    public void setTopicName(String topicName) {
        this.topicName = topicName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
