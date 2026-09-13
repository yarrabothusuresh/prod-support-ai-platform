# Payment Service Database Failure Runbook

## Document Metadata
- **Application**: payment-service
- **Environment**: local
- **Type**: RUNBOOK
- **Version**: 1.2
- **Owner**: Payments-Core SRE Team
- **Last Reviewed**: 2026-09-10

---

## 1. Overview & Scope
This runbook covers operational triage and recovery procedures when `payment-service` encounters database connection timeouts, connectivity loss, or connection pool exhaustion with the primary PostgreSQL datastore (`postgres-db`).

---

## 2. Symptoms & Alert Indicators
- **Log Exceptions**:
  - `org.postgresql.util.PSQLException: Connection to localhost:5432 refused`
  - `org.hibernate.exception.JDBCConnectionException`
  - `DatabaseTimeoutException: Connection to postgres-db timed out after 3000ms`
  - `CannotGetJdbcConnectionException: Failed to obtain JDBC Connection`
- **Telemetry Indicators**:
  - `/actuator/health` reports status `DOWN` with `db` component error.
  - `/support/dependencies` reports `postgres-db` status as `DOWN` with latency > 3000ms.
  - Ring buffer `/support/errors` shows elevated `DatabaseConnectionException` counts.

---

## 3. Diagnostic Steps
1. **Verify Database Health**:
   - Check if PostgreSQL container or host is running:
     ```bash
     docker ps --filter "name=prod-support-postgres"
     ```
   - Verify port 5432 connectivity:
     ```bash
     pg_isready -h localhost -p 5432 -U prod_support
     ```
2. **Check Connection Pool Status**:
   - Inspect HikariCP active vs. idle connections in service metrics.
   - If active connections equal `maximum-pool-size` (default: 10), connection pool starvation is occurring.
3. **Verify Network Connectivity**:
   - Confirm firewall rules, container network bridge, and DNS resolution for host `postgres-db`.
4. **Check Recent Errors**:
   - Check `/support/errors?limit=10` on `payment-service` to determine if timeouts are query-specific or connection-level.

---

## 4. Recovery & Mitigation Procedures
1. **Database Service Restart (Containerized/Local)**:
   - If the database process is stopped, restart it:
     ```bash
     docker compose restart postgres
     ```
2. **Clear Simulated Outages**:
   - If in staging/local and fault injection was triggered, clear simulated overrides:
     ```bash
     curl -X DELETE http://localhost:8081/api/payments/simulate/dependency
     ```
3. **Connection Pool Expansion**:
   - If high concurrent load caused pool exhaustion without DB failure, increase `spring.datasource.hikari.maximum-pool-size` from 10 to 25.
4. **Escalation Protocol**:
   - If PostgreSQL remains unreachable after 5 minutes, escalate to DBA on-call (`#dba-support` or PagerDuty `DB-Tier-1`).
   - Do NOT attempt manual schema changes or drop databases in production.
