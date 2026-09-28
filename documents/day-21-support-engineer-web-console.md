# Day 21 — Production Support Engineer Web Console

## Goal
Provide a usable web interface so support engineers do not need curl or Swagger for normal workflows.

## Recommended Frontend
Use React + TypeScript + Vite unless the repository already contains a preferred frontend standard.

## Screens
Create:
- Dashboard
- Applications
- Active Incidents
- Incident Details
- AI Investigation
- Metrics
- Logs
- Traces
- Kafka
- Database
- Knowledge Base
- Administration

## Incident Detail Experience
Show one chronological evidence timeline containing:
- alert;
- metrics;
- logs;
- trace evidence;
- Kafka evidence;
- database evidence;
- AI analysis;
- runbook recommendation;
- human notes;
- status and assignment changes.

## AI Presentation
Visually separate:
- Observed Facts
- AI Interpretation
- Suggested Actions

Never display an AI inference as though it were confirmed telemetry.

## UX Requirements
Include:
- loading, error and empty states;
- environment indicator;
- severity and status visibility;
- filters;
- accessible forms;
- responsive layout;
- confirmation for sensitive actions such as resolving incidents.

## Security
Integrate the Day 14 authentication/authorization model. The UI may hide unavailable actions, but server-side authorization remains mandatory.

## Testing
Add component and API tests for the critical workflows.

## Acceptance Criteria
- An engineer can open an incident and launch an investigation from the UI.
- Evidence sources are visible.
- Role restrictions are respected.
- Frontend build and test commands are documented.

## Suggested Commit Messages
- feat(day21): add production support web console
- feat(day21): add incident and investigation views
- feat(day21): add observability evidence timeline
- test(day21): add frontend workflow tests
