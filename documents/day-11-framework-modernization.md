# Day 11 — Framework Modernization

## Role
Act as a Senior Java/Spring AI Architect. Work only in this repository and first review the existing Day 1–10 implementation before changing code.

## Goal
Modernize the technical baseline without recreating existing functionality.

Current baseline visible in the repository:
- Java 21
- Spring Boot 3.3.4
- Spring AI 1.0.0-M3
- Ollama
- pgvector RAG
- Kafka diagnostics
- database diagnostics
- Elasticsearch/Kibana
- OpenTelemetry/Jaeger
- Prometheus/Grafana

## Tasks
1. Upgrade to a stable, mutually compatible Spring Boot + Spring AI baseline.
2. Remove milestone repositories if no longer required.
3. Migrate Spring AI tool-calling APIs and any deprecated configuration.
4. Verify Ollama chat, embeddings, pgvector, tool calling, investigation flow and RAG.
5. Preserve all existing public REST contracts unless a breaking change is unavoidable and documented.
6. Do not add new business features today.
7. Keep Java 21.
8. Update README with exact versions and migration notes.

## Tests
Run the complete Maven test suite. Fix migration regressions across all modules. Keep normal PR tests hermetic and independent of a live Ollama instance.

## Acceptance Criteria
- mvn clean test succeeds.
- All Day 1–10 capabilities still compile and behave consistently.
- No obsolete milestone-only dependency remains unless explicitly justified.
- README documents versions and compatibility.

## Deliverables
At the end report:
1. files changed;
2. dependency/version changes;
3. breaking API changes, if any;
4. test results;
5. known limitations.

## Suggested Commit Messages
- chore(day11): upgrade Spring Boot and Spring AI baseline
- refactor(day11): migrate AI tool calling to stable APIs
- test(day11): stabilize regression suite after framework upgrade
- docs(day11): document framework migration
