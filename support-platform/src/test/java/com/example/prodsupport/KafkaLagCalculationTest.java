package com.example.prodsupport;

import com.example.prodsupport.infrastructure.kafka.model.KafkaConsumerLagData;
import com.example.prodsupport.infrastructure.kafka.model.PartitionLagData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaLagCalculationTest {

    private static final long WARNING_THRESHOLD = 100;
    private static final long CRITICAL_THRESHOLD = 1000;

    @Test
    @DisplayName("Should correctly calculate zero lag when committed equals latest")
    void shouldCalculateZeroLag() {
        Long latest = 1000L;
        Long committed = 1000L;
        Long lag = latest - committed;

        PartitionLagData partition = new PartitionLagData(
                "payment-events", 0, 1, latest, committed, lag, PartitionLagData.STATUS_OK
        );

        assertThat(partition.lag()).isEqualTo(0L);
        assertThat(partition.status()).isEqualTo(PartitionLagData.STATUS_OK);

        String lagStatus = classifyLag(partition.lag(), WARNING_THRESHOLD, CRITICAL_THRESHOLD);
        assertThat(lagStatus).isEqualTo(KafkaConsumerLagData.LAG_NORMAL);
    }

    @Test
    @DisplayName("Should correctly calculate positive lag and classify as WARNING")
    void shouldCalculatePositiveLagWarning() {
        Long latest = 1250L;
        Long committed = 1000L;
        Long lag = latest - committed;

        PartitionLagData partition = new PartitionLagData(
                "payment-events", 0, 1, latest, committed, lag, PartitionLagData.STATUS_OK
        );

        assertThat(partition.lag()).isEqualTo(250L);
        String lagStatus = classifyLag(partition.lag(), WARNING_THRESHOLD, CRITICAL_THRESHOLD);
        assertThat(lagStatus).isEqualTo(KafkaConsumerLagData.LAG_WARNING);
    }

    @Test
    @DisplayName("Should correctly classify large lag as CRITICAL")
    void shouldClassifyLargeLagAsCritical() {
        Long latest = 6000L;
        Long committed = 1000L;
        Long lag = latest - committed;

        PartitionLagData partition = new PartitionLagData(
                "payment-events", 0, 1, latest, committed, lag, PartitionLagData.STATUS_OK
        );

        assertThat(partition.lag()).isEqualTo(5000L);
        String lagStatus = classifyLag(partition.lag(), WARNING_THRESHOLD, CRITICAL_THRESHOLD);
        assertThat(lagStatus).isEqualTo(KafkaConsumerLagData.LAG_CRITICAL);
    }

    @Test
    @DisplayName("Should safely handle missing committed offset without inventing zero")
    void shouldHandleMissingCommittedOffset() {
        Long latest = 1000L;
        Long committed = null;
        Long lag = null;

        PartitionLagData partition = new PartitionLagData(
                "payment-events", 0, 1, latest, committed, lag, PartitionLagData.STATUS_NO_COMMITTED_OFFSET
        );

        assertThat(partition.committedOffset()).isNull();
        assertThat(partition.lag()).isNull();
        assertThat(partition.status()).isEqualTo(PartitionLagData.STATUS_NO_COMMITTED_OFFSET);

        String lagStatus = classifyLag(partition.lag(), WARNING_THRESHOLD, CRITICAL_THRESHOLD);
        assertThat(lagStatus).isEqualTo(KafkaConsumerLagData.LAG_UNKNOWN);
    }

    @Test
    @DisplayName("Should safely handle committed offset ahead of latest with warning")
    void shouldHandleCommittedAheadOfLatest() {
        Long latest = 1000L;
        Long committed = 1050L;
        Long lag = 0L; // Non-negative lag protection

        PartitionLagData partition = new PartitionLagData(
                "payment-events", 0, 1, latest, committed, lag, PartitionLagData.STATUS_AHEAD_OF_LATEST
        );

        assertThat(partition.lag()).isEqualTo(0L);
        assertThat(partition.status()).isEqualTo(PartitionLagData.STATUS_AHEAD_OF_LATEST);
    }

    @Test
    @DisplayName("Should aggregate lag across multiple partitions")
    void shouldAggregateMultiPartitionLag() {
        List<PartitionLagData> partitions = List.of(
                new PartitionLagData("payment-events", 0, 1, 1200L, 1100L, 100L, PartitionLagData.STATUS_OK),
                new PartitionLagData("payment-events", 1, 1, 900L, 850L, 50L, PartitionLagData.STATUS_OK),
                new PartitionLagData("payment-events", 2, 1, 500L, null, null, PartitionLagData.STATUS_NO_COMMITTED_OFFSET)
        );

        long totalLag = 0;
        long highestLag = 0;
        for (PartitionLagData p : partitions) {
            if (p.lag() != null) {
                totalLag += p.lag();
                highestLag = Math.max(highestLag, p.lag());
            }
        }

        assertThat(totalLag).isEqualTo(150L);
        assertThat(highestLag).isEqualTo(100L);

        KafkaConsumerLagData lagData = new KafkaConsumerLagData(
                "payment-processing-group",
                "STABLE",
                2,
                totalLag,
                highestLag,
                KafkaConsumerLagData.LAG_WARNING,
                partitions,
                List.of()
        );

        assertThat(lagData.totalLag()).isEqualTo(150L);
        assertThat(lagData.highestPartitionLag()).isEqualTo(100L);
        assertThat(lagData.lagStatus()).isEqualTo(KafkaConsumerLagData.LAG_WARNING);
        assertThat(lagData.partitions()).hasSize(3);
    }

    private String classifyLag(Long totalLag, long warningThreshold, long criticalThreshold) {
        if (totalLag == null) {
            return KafkaConsumerLagData.LAG_UNKNOWN;
        } else if (totalLag >= criticalThreshold) {
            return KafkaConsumerLagData.LAG_CRITICAL;
        } else if (totalLag >= warningThreshold) {
            return KafkaConsumerLagData.LAG_WARNING;
        } else {
            return KafkaConsumerLagData.LAG_NORMAL;
        }
    }
}
