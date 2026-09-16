package com.example.prodsupport.infrastructure.kafka.model;

public record PartitionLagData(
        String topic,
        int partition,
        Integer leader,
        Long latestOffset,
        Long committedOffset,
        Long lag,
        String status
) {
    public static final String STATUS_OK = "OK";
    public static final String STATUS_NO_COMMITTED_OFFSET = "NO_COMMITTED_OFFSET";
    public static final String STATUS_AHEAD_OF_LATEST = "AHEAD_OF_LATEST";
    public static final String STATUS_UNKNOWN = "UNKNOWN";
}
