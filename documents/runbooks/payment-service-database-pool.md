# Payment Service Database Connection Pool Runbook

## Service Identity
- **Application**: `payment-service`
- **Domain**: Payments & Authorizations
- **Database**: PostgreSQL (`payment_db`)
- **Connection Pool**: HikariCP (`PaymentHikariPool`)

---

## Symptoms
- Connection acquisition timeouts (`ConnectionTimeoutException`, `SQLTransientConnectionException`).
- Elevated HTTP request latency (`> 2000ms`) on payment creation (`POST /api/payments`).
- Database pool metrics show high utilization (`> 80%` WARNING, `> 95%` CRITICAL).
- Application threads awaiting connection (`threadsAwaitingConnection > 0`).
- Upstream payment client timeout errors.

---

## Diagnostic Procedure

### 1. Verify Database Availability & Reachability
Execute the database health check tool:
```text
Tool: check_database_health(applicationName="payment-service", environment="local")
```
- Verify status is `UP` and response time is under 50ms.
- **Interpretation**: If `UP`, the PostgreSQL instance is alive and network reachability is sound.

### 2. Inspect Hikari Connection Pool Telemetry
Inspect the live Hikari connection pool status:
```text
Tool: check_database_connection_pool(applicationName="payment-service", environment="local")
```
- Observe `activeConnections`, `maxPoolSize`, `utilizationPercent`, and `threadsAwaitingConnection`.
- **Interpretation**:
  - `active = max` and `waitingThreads > 0`: Pool is saturated. New requests are blocked waiting for connections to return to the pool.
  - Saturated pool alone does NOT prove database failure. It indicates connection pool pressure.

### 3. Inspect Database Activity & Long-Running Sessions
Inspect safe aggregate activity from `pg_stat_activity`:
```text
Tool: check_database_activity(applicationName="payment-service", environment="local")
```
- Look for:
  - `waitingSessions`: Sessions blocked waiting for locks or I/O.
  - `idleInTransactionSessions`: Application code holding a transaction open while doing non-DB work (e.g., waiting on external HTTP or Kafka).
  - `longRunningQueries`: Queries running longer than the configured threshold (e.g. > 5s).

### 4. Review Recent Application Errors
```text
Tool: get_recent_errors(applicationName="payment-service", environment="local", limit=20)
```
- Check for connection acquisition errors or downstream timeout exceptions.

---

## Root Cause Interpretation: Fact vs Inference

| Observed Metric | Valid Inference | Invalid / Premature Inference |
|---|---|---|
| `active=10, max=10, waitingThreads=4` | High connection pool pressure | "There is a connection leak" (leak requires sustained exhaustion over time without traffic) |
| `database status = UP` | Database engine is reachable | "Database has no performance issues" |
| `idleInTransactionSessions > 0` | Application transactions are remaining open | "Database lock deadlock" |
| `longRunningQueries > 0` | Slow queries are holding connections active | "The database server CPU is pegged" |

---

## Safe Operational Actions

> [!IMPORTANT]
> All AI diagnostic operations are strictly **read-only**.

### Safe Immediate Actions:
1. Inspect slow query metrics in Prometheus / database APM if available.
2. Check upstream request volume (`POST /api/payments`) for an unexpected traffic surge.
3. Check Kafka consumer processing rate: if consumer processing is delayed, it may hold transactions longer.
4. Verify if pool saturation subsides once traffic returns to normal.

### Prohibited Actions (Never automate or execute without change approval):
- Do NOT terminate database sessions (`pg_terminate_backend`) without DBA approval.
- Do NOT restart the PostgreSQL database server.
- Do NOT alter table schemas, drop indexes, or modify pool size on running production nodes without standard deployment procedures.
- Do NOT execute arbitrary `SELECT`, `UPDATE`, or `DELETE` statements.
