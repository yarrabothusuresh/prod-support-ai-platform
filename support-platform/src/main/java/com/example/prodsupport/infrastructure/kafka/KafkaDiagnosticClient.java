package com.example.prodsupport.infrastructure.kafka;

import com.example.prodsupport.infrastructure.kafka.model.KafkaClusterDiagnosticData;
import com.example.prodsupport.infrastructure.kafka.model.KafkaConsumerGroupData;
import com.example.prodsupport.infrastructure.kafka.model.KafkaConsumerLagData;
import com.example.prodsupport.infrastructure.kafka.model.KafkaTopicData;

import java.time.Duration;
import java.util.List;

public interface KafkaDiagnosticClient {

    KafkaClusterDiagnosticData checkCluster(List<String> bootstrapServers, Duration timeout);

    KafkaConsumerGroupData getConsumerGroupInfo(List<String> bootstrapServers, String consumerGroup, Duration timeout);

    KafkaConsumerLagData calculateConsumerLag(List<String> bootstrapServers, String consumerGroup,
                                            Duration timeout, long warningThreshold, long criticalThreshold);

    KafkaTopicData getTopicMetadata(List<String> bootstrapServers, String topic, Duration timeout);
}
