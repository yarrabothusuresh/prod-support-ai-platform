# Day 20 — Production Containers and Kubernetes/OpenShift Deployment

## Goal
Make the platform deployable beyond local docker-compose while preserving the current developer setup.

## Containers
Create production Dockerfiles using:
- multi-stage build;
- Java 21 runtime;
- non-root user;
- minimal runtime image;
- health checks;
- graceful shutdown;
- externalized configuration.

Do not commit production credentials.

## Deployment Manifests
Create deployment configuration for support-platform and references to required infrastructure.

Support configuration for:
- PostgreSQL;
- Ollama or another configured model endpoint;
- Kafka;
- Elasticsearch;
- Prometheus;
- Jaeger/OTLP.

## Kubernetes/OpenShift Requirements
Include:
- Deployment;
- Service;
- ConfigMap;
- Secret references;
- readiness probe;
- liveness probe;
- startup probe;
- CPU and memory requests/limits;
- horizontal-scaling readiness;
- graceful termination.

Prefer portable Kubernetes manifests with OpenShift-specific notes or overlays only where necessary.

## Security
- run containers as non-root;
- avoid privileged containers;
- keep credentials external to Git;
- document TLS and network-policy expectations.

## Testing
Build the production image and document a smoke-test flow. Validate manifests with an available schema or lint mechanism.

## Acceptance Criteria
- docker-compose remains usable for local development.
- production image starts with environment-driven configuration.
- probes map to real application health endpoints.
- deployment documentation is complete.

## Suggested Commit Messages
- build(day20): add production container image
- deploy(day20): add Kubernetes and OpenShift manifests
- deploy(day20): configure probes resources and secret references
- docs(day20): add deployment guide
