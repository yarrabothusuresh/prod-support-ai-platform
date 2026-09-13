# Postmortem: Payment Service Timeout Incident 001

> **NOTICE: HISTORICAL INCIDENT EVIDENCE**
> This document records a historical incident for postmortem and reference purposes. 
> The symptoms, root causes, and resolutions described below occurred in the past and MUST NOT automatically be assumed to be the cause of any active or current operational incident.

---

## Document Metadata
- **Application**: payment-service
- **Environment**: local
- **Type**: INCIDENT
- **Incident ID**: INC-2026-0812
- **Incident Date**: 2026-08-12 14:22 UTC
- **Severity**: SEV-1 (Critical Outage)
- **Author / Lead**: Incident Commander Sarah Chen
- **Status**: RESOLVED & CLOSED

---

## 1. Incident Summary & Symptoms
On August 12, 2026, `payment-service` experienced an elevated error spike where customer checkout transactions failed with HTTP 500 errors. 
Reported symptoms included:
- Elevated checkout transaction latency (> 5000ms).
- Sudden spike in `DatabaseTimeoutException: Connection to postgres-db timed out after 3000ms`.
- Client-facing error: `PaymentProcessingException: Internal payment gateway failure`.

---

## 2. Observed Telemetry & Evidence
- **Telemetry Probes**:
  - `/actuator/health`: Reported `DOWN` with database health check failing.
  - `/support/dependencies`: `postgres-db` status was `DOWN` with 100% timeout rate.
  - `/support/errors`: 48 consecutive `CannotGetJdbcConnectionException` records captured in the ring buffer.
- **Infrastructure Metrics**:
  - PostgreSQL container CPU utilization was low (< 5%).
  - Active HikariCP connections in `payment-service` reached maximum ceiling (10 of 10 connections permanently busy).

---

## 3. Root Cause Analysis (RCA)
- **Root Cause**: Database connection pool exhaustion caused by an unclosed ResultSet in an analytical reporting query running on the primary application datasource.
- **Mechanism**: Long-running background transactions acquired database connections without returning them to the HikariCP pool, starving incoming customer payment requests until the 3000ms connection timeout was breached.

---

## 4. Resolution & Recovery
1. **Immediate Mitigation**:
   - Restarted `payment-service` instances to flush the stalled connection pool.
   - Temporarily scaled `maximum-pool-size` from 10 to 20 to provide headroom.
2. **Permanent Fix**:
   - Wrapped reporting queries in Spring `@Transactional(readOnly = true)` with automatic `try-with-resources` connection cleanup.
   - Separated heavy reporting queries to a dedicated read-replica datasource.

---

## 5. Lessons Learned & Action Items
- Never mix long-running reporting queries with user-facing transactional payment pathways.
- Configure HikariCP `leakDetectionThreshold: 2000ms` to proactively flag unclosed connections.
- Ensure automated alerts trigger when connection pool utilization exceeds 80%.
