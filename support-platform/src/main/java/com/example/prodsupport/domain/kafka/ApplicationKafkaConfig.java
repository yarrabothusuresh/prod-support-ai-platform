package com.example.prodsupport.domain.kafka;

import com.example.prodsupport.domain.RegisteredApplication;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Entity
@Table(
        name = "application_kafka_config",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_app_kafka_config_app", columnNames = {"application_id"})
        }
)
public class ApplicationKafkaConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "application_id", nullable = false, unique = true)
    private RegisteredApplication application;

    @Column(name = "bootstrap_servers", nullable = false, length = 500)
    private String bootstrapServers;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @OneToMany(mappedBy = "kafkaConfig", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonManagedReference
    private Set<ApplicationKafkaConsumerGroup> consumerGroups = new LinkedHashSet<>();

    @OneToMany(mappedBy = "kafkaConfig", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonManagedReference
    private Set<ApplicationKafkaTopic> topics = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public ApplicationKafkaConfig() {
    }

    public ApplicationKafkaConfig(RegisteredApplication application, String bootstrapServers, boolean enabled) {
        this.application = application;
        this.bootstrapServers = bootstrapServers;
        this.enabled = enabled;
    }

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public RegisteredApplication getApplication() {
        return application;
    }

    public void setApplication(RegisteredApplication application) {
        this.application = application;
    }

    public String getBootstrapServers() {
        return bootstrapServers;
    }

    public void setBootstrapServers(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Set<ApplicationKafkaConsumerGroup> getConsumerGroups() {
        return consumerGroups;
    }

    public void setConsumerGroups(Set<ApplicationKafkaConsumerGroup> consumerGroups) {
        this.consumerGroups = consumerGroups;
    }

    public Set<ApplicationKafkaTopic> getTopics() {
        return topics;
    }

    public void setTopics(Set<ApplicationKafkaTopic> topics) {
        this.topics = topics;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<String> getBootstrapServerList() {
        if (bootstrapServers == null || bootstrapServers.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(bootstrapServers.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    public Set<String> getConsumerGroupNames() {
        if (consumerGroups == null) {
            return Collections.emptySet();
        }
        return consumerGroups.stream()
                .filter(ApplicationKafkaConsumerGroup::isEnabled)
                .map(ApplicationKafkaConsumerGroup::getConsumerGroup)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public Set<String> getTopicNames() {
        if (topics == null) {
            return Collections.emptySet();
        }
        return topics.stream()
                .filter(ApplicationKafkaTopic::isEnabled)
                .map(ApplicationKafkaTopic::getTopicName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public boolean hasConsumerGroup(String consumerGroup) {
        if (consumerGroup == null || consumerGroup.isBlank()) {
            return false;
        }
        return getConsumerGroupNames().stream()
                .anyMatch(cg -> cg.equalsIgnoreCase(consumerGroup.trim()));
    }

    public boolean hasTopic(String topic) {
        if (topic == null || topic.isBlank()) {
            return false;
        }
        return getTopicNames().stream()
                .anyMatch(t -> t.equalsIgnoreCase(topic.trim()));
    }

    public void addConsumerGroup(String group) {
        if (group != null && !group.isBlank()) {
            this.consumerGroups.add(new ApplicationKafkaConsumerGroup(this, group.trim(), true));
        }
    }

    public void addTopic(String topic) {
        if (topic != null && !topic.isBlank()) {
            this.topics.add(new ApplicationKafkaTopic(this, topic.trim(), true));
        }
    }
}
