package com.example.prodsupport.infrastructure.kafka;

import com.example.prodsupport.infrastructure.kafka.model.*;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.ConsumerGroupState;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.GroupIdNotFoundException;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Component
public class ApacheKafkaDiagnosticClient implements KafkaDiagnosticClient {

    private static final Logger log = LoggerFactory.getLogger(ApacheKafkaDiagnosticClient.class);

    protected AdminClient createAdminClient(List<String> bootstrapServers, Duration timeout) {
        Properties props = new Properties();
        String servers = String.join(",", bootstrapServers);
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, servers);
        int timeoutMs = (int) Math.max(1000, timeout.toMillis());
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, timeoutMs);
        props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, timeoutMs);
        props.put(AdminClientConfig.RETRIES_CONFIG, 1);
        props.put(AdminClientConfig.CLIENT_ID_CONFIG, "support-platform-admin-" + UUID.randomUUID().toString().substring(0, 8));
        return AdminClient.create(props);
    }

    @Override
    public KafkaClusterDiagnosticData checkCluster(List<String> bootstrapServers, Duration timeout) {
        long timeoutMs = timeout.toMillis();
        try (AdminClient admin = createAdminClient(bootstrapServers, timeout)) {
            DescribeClusterResult clusterResult = admin.describeCluster(
                    new DescribeClusterOptions().timeoutMs((int) timeoutMs)
            );

            String clusterId = clusterResult.clusterId().get(timeoutMs, TimeUnit.MILLISECONDS);
            Collection<Node> nodes = clusterResult.nodes().get(timeoutMs, TimeUnit.MILLISECONDS);

            List<String> nodeInfo = nodes.stream()
                    .map(n -> n.host() + ":" + n.port() + " (id=" + n.idString() + ")")
                    .collect(Collectors.toList());

            return new KafkaClusterDiagnosticData(true, clusterId, nodes.size(), nodeInfo, null);
        } catch (Exception ex) {
            String errorMsg = cleanErrorMessage(ex);
            log.warn("Kafka cluster connectivity check failed for {}: {}", bootstrapServers, errorMsg);
            return KafkaClusterDiagnosticData.unreachable("Kafka cluster unreachable: " + errorMsg);
        }
    }

    @Override
    public KafkaConsumerGroupData getConsumerGroupInfo(List<String> bootstrapServers, String consumerGroup, Duration timeout) {
        long timeoutMs = timeout.toMillis();
        try (AdminClient admin = createAdminClient(bootstrapServers, timeout)) {
            DescribeConsumerGroupsResult result = admin.describeConsumerGroups(
                    List.of(consumerGroup),
                    new DescribeConsumerGroupsOptions().timeoutMs((int) timeoutMs)
            );

            ConsumerGroupDescription desc = result.describedGroups().get(consumerGroup).get(timeoutMs, TimeUnit.MILLISECONDS);
            String state = desc.state() != null ? desc.state().toString() : "UNKNOWN";
            int memberCount = desc.members() != null ? desc.members().size() : 0;
            String coordinator = desc.coordinator() != null ? desc.coordinator().host() + ":" + desc.coordinator().port() : "unknown";

            List<ConsumerGroupMemberData> members = new ArrayList<>();
            if (desc.members() != null) {
                for (MemberDescription m : desc.members()) {
                    List<String> partitions = m.assignment() != null ?
                            m.assignment().topicPartitions().stream()
                                    .map(tp -> tp.topic() + "-" + tp.partition())
                                    .collect(Collectors.toList()) : List.of();
                    members.add(new ConsumerGroupMemberData(m.consumerId(), m.clientId(), m.host(), partitions));
                }
            }

            return new KafkaConsumerGroupData(consumerGroup, state, memberCount, coordinator, members, null);

        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            if (cause instanceof GroupIdNotFoundException) {
                return KafkaConsumerGroupData.notFound(consumerGroup, "Configured Kafka consumer group was not found");
            }
            String errorMsg = cleanErrorMessage(ex);
            log.warn("Failed to describe consumer group '{}': {}", consumerGroup, errorMsg);
            return KafkaConsumerGroupData.notFound(consumerGroup, "Failed to inspect consumer group: " + errorMsg);
        } catch (Exception ex) {
            String errorMsg = cleanErrorMessage(ex);
            log.warn("Failed to retrieve consumer group '{}' info: {}", consumerGroup, errorMsg);
            return KafkaConsumerGroupData.notFound(consumerGroup, "Failed to inspect consumer group: " + errorMsg);
        }
    }

    @Override
    public KafkaConsumerLagData calculateConsumerLag(List<String> bootstrapServers, String consumerGroup,
                                                     Duration timeout, long warningThreshold, long criticalThreshold) {
        long timeoutMs = timeout.toMillis();
        List<String> warnings = new ArrayList<>();

        try (AdminClient admin = createAdminClient(bootstrapServers, timeout)) {
            // 1. Describe group to get state and active member assignments
            String state = "UNKNOWN";
            int memberCount = 0;
            Set<TopicPartition> targetPartitions = new LinkedHashSet<>();

            try {
                DescribeConsumerGroupsResult groupResult = admin.describeConsumerGroups(
                        List.of(consumerGroup),
                        new DescribeConsumerGroupsOptions().timeoutMs((int) timeoutMs)
                );
                ConsumerGroupDescription desc = groupResult.describedGroups().get(consumerGroup).get(timeoutMs, TimeUnit.MILLISECONDS);
                state = desc.state() != null ? desc.state().toString() : "UNKNOWN";
                memberCount = desc.members() != null ? desc.members().size() : 0;

                if (desc.members() != null) {
                    for (MemberDescription m : desc.members()) {
                        if (m.assignment() != null && m.assignment().topicPartitions() != null) {
                            targetPartitions.addAll(m.assignment().topicPartitions());
                        }
                    }
                }
            } catch (ExecutionException ex) {
                if (ex.getCause() instanceof GroupIdNotFoundException) {
                    warnings.add("Configured Kafka consumer group '" + consumerGroup + "' was not found in the cluster");
                    return new KafkaConsumerLagData(consumerGroup, "NOT_FOUND", 0, null, null,
                            KafkaConsumerLagData.LAG_UNKNOWN, List.of(), warnings);
                }
                warnings.add("Consumer group description unavailable: " + cleanErrorMessage(ex));
            } catch (Exception ex) {
                warnings.add("Consumer group description error: " + cleanErrorMessage(ex));
            }

            // 2. Fetch committed offsets
            Map<TopicPartition, OffsetAndMetadata> committedOffsets = Collections.emptyMap();
            try {
                ListConsumerGroupOffsetsResult offsetsResult = admin.listConsumerGroupOffsets(
                        consumerGroup,
                        new ListConsumerGroupOffsetsOptions().timeoutMs((int) timeoutMs)
                );
                committedOffsets = offsetsResult.partitionsToOffsetAndMetadata().get(timeoutMs, TimeUnit.MILLISECONDS);
                if (committedOffsets != null) {
                    targetPartitions.addAll(committedOffsets.keySet());
                }
            } catch (Exception ex) {
                warnings.add("Committed offsets unavailable: " + cleanErrorMessage(ex));
            }

            if (targetPartitions.isEmpty()) {
                if (ConsumerGroupState.DEAD.toString().equalsIgnoreCase(state)) {
                    warnings.add("Consumer group is DEAD and has no assigned or committed partitions");
                } else if (ConsumerGroupState.EMPTY.toString().equalsIgnoreCase(state)) {
                    warnings.add("Consumer group is EMPTY and has no committed partition offsets");
                } else {
                    warnings.add("No partition assignments or committed offsets found for consumer group '" + consumerGroup + "'");
                }
                return new KafkaConsumerLagData(consumerGroup, state, memberCount, 0L, 0L,
                        KafkaConsumerLagData.LAG_NORMAL, List.of(), warnings);
            }

            // 3. Fetch latest partition offsets
            Map<TopicPartition, OffsetSpec> offsetSpecMap = new HashMap<>();
            for (TopicPartition tp : targetPartitions) {
                offsetSpecMap.put(tp, OffsetSpec.latest());
            }

            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> latestOffsets = Collections.emptyMap();
            try {
                ListOffsetsResult listOffsetsResult = admin.listOffsets(
                        offsetSpecMap,
                        new ListOffsetsOptions().timeoutMs((int) timeoutMs)
                );
                latestOffsets = listOffsetsResult.all().get(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (Exception ex) {
                warnings.add("Latest partition offsets unavailable: " + cleanErrorMessage(ex));
            }

            // 4. Calculate partition-level lag
            List<PartitionLagData> partitionResults = new ArrayList<>();
            long totalLag = 0;
            long highestLag = 0;
            boolean hasValidLag = false;

            for (TopicPartition tp : targetPartitions) {
                Long latest = (latestOffsets != null && latestOffsets.get(tp) != null) ?
                        latestOffsets.get(tp).offset() : null;

                OffsetAndMetadata committedMeta = committedOffsets != null ? committedOffsets.get(tp) : null;
                Long committed = committedMeta != null ? committedMeta.offset() : null;

                Long lag = null;
                String status;

                if (committed == null) {
                    status = PartitionLagData.STATUS_NO_COMMITTED_OFFSET;
                } else if (latest != null) {
                    if (committed > latest) {
                        lag = 0L;
                        status = PartitionLagData.STATUS_AHEAD_OF_LATEST;
                        warnings.add("Committed offset (" + committed + ") is ahead of latest offset (" + latest +
                                ") on partition " + tp.topic() + "-" + tp.partition());
                        hasValidLag = true;
                    } else {
                        lag = latest - committed;
                        status = PartitionLagData.STATUS_OK;
                        totalLag += lag;
                        highestLag = Math.max(highestLag, lag);
                        hasValidLag = true;
                    }
                } else {
                    status = PartitionLagData.STATUS_UNKNOWN;
                }

                partitionResults.add(new PartitionLagData(
                        tp.topic(),
                        tp.partition(),
                        null,
                        latest,
                        committed,
                        lag,
                        status
                ));
            }

            // Sort by topic then partition number
            partitionResults.sort(Comparator.comparing(PartitionLagData::topic)
                    .thenComparingInt(PartitionLagData::partition));

            // 5. Classify lag against thresholds
            Long reportedTotalLag = hasValidLag ? totalLag : null;
            Long reportedHighestLag = hasValidLag ? highestLag : null;
            String lagStatus;

            if (reportedTotalLag == null) {
                lagStatus = KafkaConsumerLagData.LAG_UNKNOWN;
            } else if (reportedTotalLag >= criticalThreshold) {
                lagStatus = KafkaConsumerLagData.LAG_CRITICAL;
            } else if (reportedTotalLag >= warningThreshold) {
                lagStatus = KafkaConsumerLagData.LAG_WARNING;
            } else {
                lagStatus = KafkaConsumerLagData.LAG_NORMAL;
            }

            return new KafkaConsumerLagData(
                    consumerGroup,
                    state,
                    memberCount,
                    reportedTotalLag,
                    reportedHighestLag,
                    lagStatus,
                    partitionResults,
                    warnings
            );

        } catch (Exception ex) {
            String errorMsg = cleanErrorMessage(ex);
            log.warn("Failed to calculate consumer lag for group '{}': {}", consumerGroup, errorMsg);
            warnings.add("Kafka consumer lag calculation failed: " + errorMsg);
            return new KafkaConsumerLagData(consumerGroup, "UNKNOWN", 0, null, null,
                    KafkaConsumerLagData.LAG_UNKNOWN, List.of(), warnings);
        }
    }

    @Override
    public KafkaTopicData getTopicMetadata(List<String> bootstrapServers, String topic, Duration timeout) {
        long timeoutMs = timeout.toMillis();
        try (AdminClient admin = createAdminClient(bootstrapServers, timeout)) {
            DescribeTopicsResult result = admin.describeTopics(
                    List.of(topic),
                    new DescribeTopicsOptions().timeoutMs((int) timeoutMs)
            );

            TopicDescription desc = result.topicNameValues().get(topic).get(timeoutMs, TimeUnit.MILLISECONDS);
            List<TopicPartitionMetadataData> partitions = desc.partitions().stream()
                    .map(p -> new TopicPartitionMetadataData(
                            p.partition(),
                            p.leader() != null ? p.leader().id() : -1,
                            p.replicas().stream().map(Node::id).collect(Collectors.toList()),
                            p.isr().stream().map(Node::id).collect(Collectors.toList())
                    ))
                    .sorted(Comparator.comparingInt(TopicPartitionMetadataData::partition))
                    .collect(Collectors.toList());

            return new KafkaTopicData(topic, partitions.size(), partitions, desc.isInternal(), null);

        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof UnknownTopicOrPartitionException) {
                return KafkaTopicData.notFound(topic, "Topic '" + topic + "' was not found in the configured Kafka cluster");
            }
            String errorMsg = cleanErrorMessage(ex);
            return KafkaTopicData.notFound(topic, "Failed to retrieve topic metadata: " + errorMsg);
        } catch (Exception ex) {
            String errorMsg = cleanErrorMessage(ex);
            return KafkaTopicData.notFound(topic, "Failed to retrieve topic metadata: " + errorMsg);
        }
    }

    private String cleanErrorMessage(Throwable ex) {
        if (ex == null) return "Unknown error";
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        if (root instanceof TimeoutException) {
            return "Connection or request timed out after configured duration";
        }
        String msg = root.getMessage();
        return (msg != null && !msg.isBlank()) ? msg : root.getClass().getSimpleName();
    }
}
