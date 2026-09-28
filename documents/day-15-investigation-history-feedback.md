# Day 15 — Persistent Investigation Sessions and Feedback

## Goal
Turn stateless support questions into auditable multi-step investigation sessions.

## Implement
Create persistence models such as:
- InvestigationSession
- InvestigationMessage
- InvestigationEvidence
- ToolExecutionHistory
- InvestigationFeedback

Persist:
sessionId, user identity, application, environment, question, response summary, tools executed, evidence references, model, timestamps, latency and feedback.

## REST APIs
Implement endpoints equivalent to:
- POST /api/investigations
- POST /api/investigations/{id}/messages
- GET /api/investigations/{id}
- GET /api/investigations/{id}/timeline
- POST /api/investigations/{id}/feedback

Feedback types:
HELPFUL, NOT_HELPFUL, INCORRECT, MISSING_EVIDENCE.

## Requirements
1. Reuse existing investigation/tool audit context.
2. Store evidence references and sanitized summaries, not unsafe raw secrets.
3. Support follow-up questions in the same session.
4. Keep immutable tool execution history.
5. Allow incident IDs to reference investigation sessions.
6. Record model/provider and execution latency.
7. Add retention-friendly created/updated timestamps.

## Security
Never persist bearer tokens, passwords, API keys, private keys or unredacted connection strings.

## Tests
Cover session creation, follow-ups, timeline ordering, tool history, feedback, authorization, incident linking and secret sanitization.

## Acceptance Criteria
- Investigation history survives restart.
- Every AI answer is traceable to tool/evidence history.
- Existing chat/investigation endpoints remain compatible or have a documented migration path.

## Suggested Commit Messages
- feat(day15): persist AI investigation sessions
- feat(day15): store evidence and tool execution history
- feat(day15): add investigation feedback APIs
- test(day15): verify investigation history and sanitization
