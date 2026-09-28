# Day 14 — Authentication, RBAC and Environment-Level Authorization

## Goal
Protect production diagnostic and incident data with application-level security.

## Implement
Add Spring Security using an OAuth2/OIDC-compatible design.

Roles:
- VIEWER
- SUPPORT_ENGINEER
- SUPPORT_LEAD
- ADMIN

Example permissions:
VIEWER: read dashboards/incidents.
SUPPORT_ENGINEER: run investigations, add notes, acknowledge incidents.
SUPPORT_LEAD: assign incidents, change severity, resolve incidents.
ADMIN: register applications, manage configs and access.

## Environment-Level Access
Authorization must include both application and environment. A user allowed to inspect payment-service/DEV must not automatically gain payment-service/PROD.

## Protect
Apply authorization to:
- /api/support/**
- /api/incidents/**
- /api/applications/**
- /api/logs/**
- /api/traces/**
- /api/metrics/**
- /api/database/**
- /api/kafka/**
- configuration-changing APIs

## Preserve Existing Controls
Keep anti-SSRF validation, tool allowlists, secret masking, prompt-injection protection and read-only diagnostic restrictions.

## Local Development
Provide a simple local/dev authentication mode or test identity setup without hard-coding production credentials.

## Audit
Record user identity for investigation execution, incident updates and configuration changes.

## Tests
Cover anonymous access, role boundaries, app/environment boundaries, admin actions, forbidden access and audit identity.

## Acceptance Criteria
- Unauthorized users cannot access protected data.
- Environment scope is enforced.
- Security tests are deterministic.
- No credentials are committed.

## Suggested Commit Messages
- feat(day14): add authentication security foundation
- feat(day14): implement role based authorization
- feat(day14): enforce application environment access
- test(day14): add authorization boundary tests
