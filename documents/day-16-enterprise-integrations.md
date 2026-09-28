# Day 16 — Ticketing, Notification and On-Call Integrations

## Goal
Integrate incidents with enterprise support tools without coupling the incident core to one vendor.

## Architecture
Use ports and adapters.

Core interfaces:
- TicketingPort
- NotificationPort
- OnCallPort

Candidate adapters:
- JiraTicketingAdapter
- ServiceNowTicketingAdapter
- SlackNotificationAdapter
- TeamsNotificationAdapter
- PagerDutyOnCallAdapter

Adapters must be optional and configuration-driven.

## Features
Support:
1. create external ticket/incident;
2. update external status;
3. post incident/investigation summary;
4. send escalation notification;
5. store external system and external reference ID;
6. retry transient integration failures safely;
7. expose integration status without leaking credentials.

## Reliability
Do not make external integrations part of the core incident database transaction. If Jira/Slack/etc. is unavailable, the local incident must still be created and the delivery failure must be recorded for retry.

Consider an outbox/event approach for reliable delivery.

## Testing
Do not require real Jira, ServiceNow, Slack, Teams or PagerDuty credentials. Use fake adapters or MockWebServer contract tests.

## Security
Credentials must come from environment/secret stores only. Never log tokens or webhook secrets.

## Acceptance Criteria
- Core incident management works with all adapters disabled.
- Adapter failures are visible and recoverable.
- Vendor-specific code stays outside the domain layer.

## Suggested Commit Messages
- feat(day16): introduce incident integration ports
- feat(day16): add pluggable ticket and notification adapters
- feat(day16): persist external incident references
- test(day16): add integration adapter contract tests
