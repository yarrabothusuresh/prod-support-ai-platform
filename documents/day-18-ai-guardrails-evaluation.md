# Day 18 — AI Guardrails, Grounding and Evaluation

## Goal
Measure whether AI answers are supported by collected evidence and remain safe when source data contains misleading or untrusted text.

## Add Evaluation Metadata
Capture for each investigation:
- evidenceCount
- toolsUsed
- sourcesUsed
- grounded flag
- model/provider
- latency
- failureReason
- usage information when safely available

## Defensive Controls
Add checks for:
- untrusted instructions embedded inside logs or documents;
- attempts to influence tool selection from telemetry text;
- unsupported root-cause claims;
- sensitive-data leakage;
- unsafe operational recommendations;
- mismatch between claimed evidence and actual evidence.

When evidence is insufficient, return a clear insufficient-evidence result instead of asserting a root cause.

## Evaluation Dataset
Create version-controlled test cases under test resources covering:
- Kafka lag;
- DB pool exhaustion;
- HTTP 5xx spike;
- downstream timeout;
- trace latency;
- application outage;
- no evidence available;
- misleading log text;
- untrusted instructions inside runbooks/logs;
- conflicting evidence.

## Evaluation Assertions
Verify:
1. expected tool families are selected;
2. only approved tools execute;
3. final statements map to evidence;
4. untrusted embedded instructions do not override system behavior;
5. unsupported claims are rejected or qualified.

## Reporting
Produce machine-readable test output or summary metrics such as grounded-answer rate, unsupported-claim count and safety-control failures.

## Acceptance Criteria
- Evaluation runs without live production dependencies.
- Defensive failures are reproducible in tests.
- No private reasoning traces are persisted.
- README documents what is measured and limitations.

## Suggested Commit Messages
- feat(day18): add AI grounding and safety evaluation
- feat(day18): validate evidence alignment and untrusted input handling
- test(day18): create support AI evaluation dataset
- docs(day18): document AI quality metrics
