# Day 19 — GitHub Actions CI/CD and Repository Quality

## Goal
Automatically validate pull requests and main-branch changes.

## Implement
Add a GitHub Actions workflow for the Java monorepo.

Recommended stages:
1. checkout;
2. setup Java 21;
3. restore Maven dependency cache;
4. compile;
5. run unit tests;
6. run hermetic integration tests;
7. package;
8. publish test reports.

## Quality Checks
Add practical repository checks such as:
- dependency vulnerability review;
- SBOM generation;
- accidental credential detection;
- static analysis;
- container image analysis when images are built.

Keep live Ollama, Elasticsearch, Kafka, Jaeger and external-service tests behind explicit profiles so normal pull-request validation remains reliable.

## Build Rules
- Fail on compile or test failures.
- Do not place credentials in workflow files.
- Use trusted action versions.
- Publish useful test diagnostics.
- Keep the workflow reproducible.

## Optional Coverage
Add coverage reporting if it can be done without making the build brittle. Document agreed thresholds instead of inventing arbitrary numbers.

## Acceptance Criteria
- Pull requests trigger validation.
- Main branch changes are validated.
- Normal CI does not require developer-laptop services.
- Test reports are available from workflow runs.
- README explains the pipeline.

## Suggested Commit Messages
- ci(day19): add GitHub Actions build and test workflow
- ci(day19): add dependency and repository quality checks
- test(day19): publish automated test reports
- docs(day19): document CI quality gates
