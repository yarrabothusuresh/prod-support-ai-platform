# Payment Service Kafka Consumer Lag Runbook

## Metadata
- **Document Type**: RUNBOOK
- **Application**: payment-service
- **Environment**: local, dev, staging, prod
- **Owner**: payments-platform-team
- **Version**: 1.0.0
- **Last Updated**: 2026-09-16

## Symptoms
- Delayed payment processing and order confirmation latency
- Increasing consumer lag on consumer group `payment-processing-group`
- Backlog accumulating across `payment-events` partitions
- Complaints of payments stuck in pending or submitted states

## Diagnostic Checks

1. **Verify Consumer Group State**:
   - Check consumer group state (`STABLE`, `EMPTY`, `PREPARING_REBALANCE`, `DEAD`).
   - A `STABLE` state indicates normal member assignment, though backlog may still accumulate if message production outpaces consumer processing throughput.
   - An `EMPTY` state indicates zero active consumer instances assigned to the group.

2. **Check Active Consumer Members**:
   - Verify member count matches the expected deployed pod/replica count.
   - If member count is 0, verify whether the payment-service application instances or worker pods are running.

3. **Inspect Lag per Partition**:
   - Compare lag across individual partitions of `payment-events`.
   - Skewed lag (high lag on single partition, low on others) may indicate a poison pill message, uneven partition key distribution, or a stuck consumer thread.
   - Uniform lag across all partitions indicates overall consumption throughput deficit or paused processing.

4. **Check Recent Application Errors**:
   - Query recent error store for timeouts, connection pool exhaustion, deserialization errors, or downstream API failures.

5. **Verify Consumer Processing Latency**:
   - Inspect whether consumer processing loops have stalled or introduced artificial delays.

6. **Verify Downstream Dependencies**:
   - Check status of databases (`postgres-db`) and payment gateways (`fraud-detection-api`, `notification-service`).

## Important Operational Principles
- **Lag is an Observed Fact, Not Confirmed Failure**: Consumer lag reflects a temporary rate difference between event production and event consumption. Short-lived lag during traffic surges is normal.
- **Trend Verification Required**: A single point-in-time lag measurement cannot prove whether lag is increasing, decreasing, or recovering. Verify multiple observations before declaring an incident.

## Recovery Procedures
- Verify application health and restore any downed consumer pods.
- Address downstream bottleneck or transient external dependency latency.
- If processing is paused due to maintenance or fault simulation, resume the consumer container.
- **DO NOT** reset consumer offsets, skip messages, delete topics, or restart production consumers without explicit change management authorization.
