# Day 12 — Production Incident Lifecycle

## Role
Act as a Senior Java/Spring Production Support Architect. Reuse the existing diagnostics, investigation and persistence patterns.

## Goal
Move the platform from diagnosis-only behavior to managed production incidents.

## Implement
Create an incident domain with entities such as:
- Incident
- IncidentEvidence
- IncidentActivity
- IncidentAssignment
- IncidentStatusHistory

Suggested incident fields:
id, incidentNumber, applicationName, environment, title, description, severity, status, owner, createdAt, acknowledgedAt, resolvedAt, closedAt, rootCause, resolution, createdBy, updatedAt.

Severity:
SEV1, SEV2, SEV3, SEV4.

Status:
OPEN, ACKNOWLEDGED, INVESTIGATING, MITIGATED, RESOLVED, CLOSED.

Create Flyway migration V8__create_incident_management_tables.sql.

## REST APIs
Implement endpoints equivalent to:
- POST /api/incidents
- GET /api/incidents/{id}
- GET /api/incidents
- PATCH /api/incidents/{id}/status
- PATCH /api/incidents/{id}/assign
- POST /api/incidents/{id}/evidence

Attach existing investigation results as evidence without copying secrets.

## Rules
- Validate legal lifecycle transitions.
- Keep full audit history.
- Do not let the LLM silently close or resolve an incident.
- Application/environment must map to registered applications.
- Existing diagnostic tools remain read-only.

## Tests
Cover incident creation, filtering, assignment, evidence, legal/illegal transitions, severity validation, disabled/unknown applications and audit history.

## Acceptance Criteria
- Incident lifecycle persists correctly.
- Invalid transitions return deterministic validation errors.
- Existing Day 1–11 tests still pass.
- README includes sample curl/PowerShell flow.

## Suggested Commit Messages
- feat(day12): add production incident domain and lifecycle
- feat(day12): expose incident management REST APIs
- test(day12): add incident lifecycle tests
- docs(day12): document production incident workflow
