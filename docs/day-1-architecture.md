# Day 1 Architecture — AI Production Support Platform

## Overview
Day 1 establishes a modular, decoupled foundation for onboarding microservices into an AI production support platform:

```
+------------------------------------+
|          payment-service           |
|            (port 8081)             |
+------------------------------------+
                  |
                  | embeds
                  v
+------------------------------------+
| support-agent-spring-boot-starter  |
|      - exposes /support/info       |
|      - integrates Actuator health  |
+------------------------------------+
                  ^
                  | REST test-connection / info
                  |
+------------------------------------+
|          support-platform          |
|            (port 8080)             |
|   - Application Registry           |
|   - RestClient health probe        |
+------------------------------------+
                  |
                  | Flyway / JPA
                  v
+------------------------------------+
|        PostgreSQL Database         |
|            (port 5432)             |
+------------------------------------+
```

## Reusable Starter Contract
Any application including `support-agent-spring-boot-starter` and setting `prod-support.enabled=true` automatically gains:
- `GET /support/info` returning application metadata and Actuator health status.
- Zero business logic leakage from client apps into the starter.
