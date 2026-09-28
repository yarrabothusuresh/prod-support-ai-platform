# AI Production Support Platform — Screen Specifications

## 1. Overview Dashboard

### Primary action
Open the most urgent incident.

### Layout
1. Header with title, environment scope and refresh.
2. Five KPI cards.
3. Critical incidents table.
4. Service health matrix.
5. Alert/error trend chart.
6. SLA/escalation panel.
7. Recent investigations.

### States
- Loading: skeleton cards/table.
- Empty: "No active production incidents."
- Partial failure: affected widget shows source-specific error without blanking the dashboard.
- Stale data: show last updated timestamp.

---

## 2. Incident List

### Controls
- search;
- severity filter;
- status filter;
- application filter;
- environment filter;
- owner filter;
- SLA state;
- date range.

### Table columns
Severity, incident ID, title, application, environment, status, owner, age, SLA, updated.

### Row actions
Open, acknowledge, assign.

Bulk actions should be limited to safe cases. Do not bulk-resolve incidents.

---

## 3. Incident Detail

### Header
- severity badge;
- incident ID;
- title;
- application/environment;
- status;
- owner;
- SLA timer.

### Tabs
- Summary
- Timeline
- Evidence
- Investigation
- Runbook
- Activity

### Summary blocks
- customer/service impact;
- current symptoms;
- evidence-backed probable cause;
- mitigation;
- next action;
- missing evidence.

### Right rail
- acknowledge;
- assign;
- change severity;
- escalate;
- mitigate;
- resolve.

Permissions must drive availability.

---

## 4. Investigation Workspace

### Desktop layout
- Left: context
- Center: conversation
- Right: evidence

### Context panel
- application;
- environment;
- incident;
- time window;
- correlation ID;
- trace ID.

### Conversation area
Each AI response uses structured sections:
- Summary
- Observed Facts
- Interpretation
- Missing Evidence
- Recommended Next Checks
- Sources / Tools Used

### Tool status
Show readable progress:
- Checking application health
- Querying metrics
- Searching logs
- Inspecting traces
- Checking Kafka
- Checking database
- Searching runbooks

Do not expose private model reasoning.

---

## 5. Evidence Drawer

Each evidence card contains:
- type;
- source system;
- timestamp;
- application/environment;
- concise value/summary;
- reliability state;
- deep-link action.

Example:

```text
[METRIC] HTTP 5xx rate
payment-service · PROD
14.2% during 14:20–14:28
Source: Prometheus
[Open chart]
```

---

## 6. Metrics Screen

### Header
Application, environment, time range, refresh.

### Default panels
- request rate;
- error rate;
- latency p50/p95/p99;
- JVM heap;
- CPU;
- thread count;
- DB pool;
- active alerts.

### Interaction
Selecting a chart interval can launch:
- logs in same window;
- traces in same window;
- AI investigation with context prefilled.

---

## 7. Logs Screen

### Search controls
- text query;
- level;
- exception;
- correlation ID;
- trace ID;
- time;
- application/environment.

### Views
- Events
- Error patterns
- Timeline

Use monospaced font for raw log content.

Sensitive values remain masked.

---

## 8. Traces Screen

### Search
- trace ID;
- application;
- operation;
- duration;
- error only;
- time.

### Trace detail
Use waterfall visualization plus span detail pane.

Actions:
- related logs;
- related metrics;
- attach to incident;
- start investigation.

---

## 9. Kafka Screen

### Cards
- broker/cluster status;
- topics;
- consumer groups;
- total lag;
- critical groups.

### Consumer detail
- topic;
- partition;
- current offset;
- end offset;
- lag;
- consumer member;
- state.

Use warning/critical thresholds from backend configuration.

---

## 10. Database Screen

### Cards
- connectivity;
- connection pool utilization;
- active sessions;
- slow activity;
- configured limits.

### Safety
All diagnostic interactions remain read-only.

Do not expose raw credentials or unrestricted SQL consoles.

---

## 11. Knowledge Screen

### Layout
Left filters + central results.

Result card:
- title;
- source type;
- application relevance;
- updated date;
- semantic score where useful;
- excerpt;
- open source.

Source types:
Runbook, Architecture, Procedure, Incident, Troubleshooting.

---

## 12. Application Registry

### Table
- application;
- owner;
- environments;
- enabled;
- diagnostics;
- logging;
- tracing;
- metrics;
- Kafka;
- database.

### Actions
Open, edit, test connectivity, disable.

---

## 13. Application Onboarding Wizard

### Step 1 — Identity
Application name, team, owner.

### Step 2 — Environment
Environment name and diagnostic base URL.

### Step 3 — Telemetry
Optional integration cards:
- Logs
- Metrics
- Traces
- Kafka
- Database

Each card states Connected / Not configured / Error.

### Step 4 — Access
Assign allowed roles/users/groups.

### Step 5 — Test
Run safe connection tests.

### Step 6 — Review
Show all configuration excluding secret values.

### Step 7 — Activate
Create/enable registered application.

---

## 14. Administration

Sections:
- Users and roles
- Application access
- SLA policies
- Escalation policies
- External integrations
- Audit history

Changes should include:
who, what, old/new value when safe, timestamp.

---

# Shared Component Specifications

## Severity Badge

Variants:
- SEV1 Critical
- SEV2 High
- SEV3 Medium
- SEV4 Low

Must show both text and color.

## Status Badge

Open, Acknowledged, Investigating, Mitigated, Resolved, Closed.

## Environment Badge

PROD, UAT, QA, DEV, LOCAL.

PROD should have the strongest visual emphasis but avoid alarming styling when no incident exists.

## Evidence Card

Fields:
source type, title, timestamp, context, summary, source state, actions.

## SLA Timer

States:
- healthy;
- approaching;
- breached.

Always show actual remaining/overdue time text.

---

# Interaction Standards

## Confirmation required
- resolve incident;
- close incident;
- severity downgrade for critical incident;
- disable production application;
- modify production integration configuration.

## No confirmation required
- filtering;
- opening evidence;
- refreshing data;
- running read-only diagnostic investigation.

## Toasts
Use concise action-oriented text:
- "Incident acknowledged."
- "Owner updated."
- "Investigation attached to INC-1042."
- "Prometheus is unavailable; partial evidence shown."

---

# Responsive Rules

## Wide desktop
Use full three-column investigation workspace.

## Standard desktop
Context becomes collapsible drawer.

## Tablet
Evidence becomes tab/drawer.

## Mobile
Prioritize incident summary, timeline and actions. Deep observability views may switch to simplified read-only representations.

---

# Accessibility Handoff

- logical tab order;
- skip link;
- sidebar supports keyboard;
- focus returns to trigger after modal closes;
- all form errors have inline text;
- charts have textual equivalent;
- status is never color-only;
- table header associations are explicit;
- icons have accessible names when actionable.
