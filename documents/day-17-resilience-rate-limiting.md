# Day 17 — Resilience, Rate Limiting and Failure Isolation

## Goal
Ensure the support platform degrades gracefully when Ollama, Elasticsearch, Prometheus, Jaeger, monitored services or external integrations fail.

## Implement
Introduce bounded resilience patterns:
- timeout
- retry
- circuit breaker
- bulkhead
- rate limiting

Apply where appropriate to:
- OllamaSupportAiClient
- PrometheusMetricsQueryClient
- tracing client
- Elasticsearch/log client
- registered application HTTP clients
- external ticket/notification adapters

## Rules
1. Retry only safe/idempotent operations.
2. Do not retry validation errors or arbitrary 4xx responses.
3. Use bounded retries and exponential backoff.
4. Enforce total investigation time budget.
5. If one telemetry source fails, return partial evidence instead of failing the entire investigation.
6. Distinguish dependency unavailable from no evidence found.
7. Expose circuit breaker, timeout and retry metrics through Micrometer.
8. Protect expensive AI endpoints from abuse using configurable rate limits.

## Tests
Use deterministic tests for:
- timeout;
- retry succeeds after transient failure;
- circuit opens;
- half-open recovery;
- bulkhead rejection;
- rate-limit response;
- partial evidence behavior;
- total investigation budget.

## Acceptance Criteria
- Dependency failures do not cascade through the platform.
- AI responses explicitly state when evidence is incomplete.
- Existing diagnostics remain read-only.

## Suggested Commit Messages
- feat(day17): add resilience policies to diagnostic clients
- feat(day17): implement rate limiting and investigation budgets
- feat(day17): expose resilience telemetry
- test(day17): verify retry circuit breaker and fallback behavior
