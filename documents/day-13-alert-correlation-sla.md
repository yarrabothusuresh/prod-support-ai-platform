# Day 13 — Alert Correlation, Deduplication, SLA and Escalation

## Goal
Convert related observability signals into one support incident and track acknowledgement/resolution targets.

## Reuse Existing Sources
Use the current Prometheus alerts, metrics, logs, traces, Kafka diagnostics, DB diagnostics, application health and RAG runbooks.

## Implement
Create services similar to:
- AlertCorrelationService
- IncidentDeduplicationService
- SlaPolicyService
- IncidentEscalationService

Example correlation:
high HTTP 5xx + DB pool exhaustion + matching timeout logs + slow DB spans should normally produce one correlated incident, not four.

## Requirements
1. Define a stable correlation fingerprint using application, environment, signal family and bounded time window.
2. Suppress duplicate incidents while the matching incident is active.
3. Persist all correlated evidence.
4. Support configurable SLA policies by severity.
5. Track acknowledgement and resolution deadlines.
6. Persist escalation history.
7. Expose SLA breach state through API and metrics.
8. Keep policy values configurable rather than hard-coded.

Example defaults for local demo:
- SEV1 acknowledgement: 5 minutes
- SEV2 acknowledgement: 15 minutes
- SEV3/SEV4: configurable

## AI Usage
AI may summarize probable cause, evidence and recommended next steps, but every factual statement must map to collected evidence.

## Tests
Test duplicate alerts, different applications/environments, time-window expiry, SLA breach, escalation, evidence merging and correlation stability.

## Acceptance Criteria
- Repeated equivalent alerts do not create duplicate active incidents.
- SLA state is deterministic and persisted.
- Escalations are auditable.
- Existing observability APIs remain unchanged.

## Suggested Commit Messages
- feat(day13): correlate alerts into production incidents
- feat(day13): add incident deduplication and SLA policies
- feat(day13): implement escalation tracking
- test(day13): cover correlation SLA and deduplication
