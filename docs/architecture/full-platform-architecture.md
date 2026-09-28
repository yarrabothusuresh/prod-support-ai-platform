# AI Production Support Platform — Full Architecture

## 1. Architecture Scope

This document combines the implemented Day 1–10 platform and the planned Day 11–22 evolution.

Legend:

- **Current** — capability already represented in the repository.
- **Planned** — roadmap capability to be implemented in upcoming days.

---

# 2. System Context

```mermaid
flowchart LR
    SE[Support Engineer]
    SL[Support Lead]
    DEV[Application Developer]
    ADM[Platform Admin]

    UI[Production Support Web Console\nPlanned Day 21]
    API[Support Platform\nSpring Boot + Spring AI]
    APPS[Monitored Applications\nSupport Agent Starter / Actuator]
    OBS[Observability Systems]
    AI[LLM / Ollama]
    EXT[Enterprise Integrations\nTicketing / Chat / On-call]
    IDP[OIDC Identity Provider]

    SE --> UI
    SL --> UI
    DEV --> UI
    ADM --> UI

    UI --> API
    UI --> IDP
    API --> IDP
    API --> APPS
    API --> OBS
    API --> AI
    API --> EXT
```

---

# 3. Container Architecture

```mermaid
flowchart TB
    subgraph Client
        WEB[React + TypeScript Web Console\nPlanned]
    end

    subgraph Platform["support-platform"]
        REST[REST Controllers]
        SEC[Security + RBAC\nPlanned]
        INCIDENT[Incident Management\nPlanned]
        INVEST[Investigation Service]
        TOOL[AI Diagnostic Tool Registry]
        RAG[Knowledge / RAG]
        METRICS[Metrics Diagnostics]
        LOGS[Log Diagnostics]
        TRACES[Trace Diagnostics]
        KAFKA[Kafka Diagnostics]
        DB[Database Diagnostics]
        INTEGRATION[Integration Ports\nPlanned]
        AUDIT[Audit / Investigation History\nExpanded Planned]
    end

    subgraph Data
        PG[(PostgreSQL)]
        VEC[(pgvector)]
    end

    subgraph AI
        OLLAMA[Ollama Chat Model]
        EMBED[Embedding Model]
    end

    subgraph Observability
        PROM[Prometheus]
        GRAF[Grafana]
        ELASTIC[Elasticsearch]
        KIB[Kibana]
        JAEGER[Jaeger]
        OTEL[OpenTelemetry Collector]
    end

    subgraph Messaging
        KCLUSTER[Kafka]
    end

    subgraph TargetApp
        STARTER[Support Agent Spring Boot Starter]
        ACT[Actuator / Support Endpoints]
    end

    WEB --> REST
    REST --> SEC
    SEC --> INCIDENT
    SEC --> INVEST

    INVEST --> TOOL
    INVEST --> RAG
    TOOL --> METRICS
    TOOL --> LOGS
    TOOL --> TRACES
    TOOL --> KAFKA
    TOOL --> DB

    INCIDENT --> PG
    AUDIT --> PG
    RAG --> PG
    RAG --> VEC
    RAG --> EMBED

    INVEST --> OLLAMA

    METRICS --> PROM
    LOGS --> ELASTIC
    TRACES --> JAEGER
    KAFKA --> KCLUSTER
    DB --> PG

    TOOL --> STARTER
    STARTER --> ACT

    OTEL --> JAEGER
    PROM --> GRAF
    ELASTIC --> KIB

    INCIDENT --> INTEGRATION
```

---

# 4. Backend Component Architecture

```mermaid
flowchart LR
    C1[Application Controllers]
    C2[Incident Controllers\nPlanned]
    C3[Observability Controllers]
    C4[Knowledge Controllers]

    S1[Application Registry Service]
    S2[Investigation Service]
    S3[Incident Service\nPlanned]
    S4[Correlation/SLA Service\nPlanned]
    S5[Knowledge Services]
    S6[Integration Services\nPlanned]

    T[Diagnostic Tool Registry]

    T1[Health / Dependencies]
    T2[Metrics Tools]
    T3[Log Tools]
    T4[Trace Tools]
    T5[Kafka Tools]
    T6[Database Tools]
    T7[Knowledge Tool]

    R1[(Application Repositories)]
    R2[(Incident Repositories\nPlanned)]
    R3[(Knowledge Repositories)]
    R4[(Investigation History\nPlanned)]

    C1 --> S1
    C2 --> S3
    C3 --> S2
    C4 --> S5

    S2 --> T
    S3 --> S4
    S3 --> S6

    T --> T1
    T --> T2
    T --> T3
    T --> T4
    T --> T5
    T --> T6
    T --> T7

    S1 --> R1
    S3 --> R2
    S5 --> R3
    S2 --> R4
```

---

# 5. AI Investigation Sequence

```mermaid
sequenceDiagram
    actor Engineer
    participant UI as Web Console
    participant API as Support API
    participant Auth as Access Validator
    participant INV as Investigation Service
    participant LLM as Spring AI / Ollama
    participant Tools as Diagnostic Tool Registry
    participant Obs as Telemetry Sources
    participant RAG as Knowledge / pgvector
    participant Store as Investigation Store

    Engineer->>UI: Ask "Why is payment-service failing?"
    UI->>API: Start/continue investigation
    API->>Auth: Validate user + app + environment
    Auth-->>API: Allowed
    API->>INV: Execute investigation
    INV->>LLM: Question + safe context
    LLM->>Tools: Request approved diagnostic tools
    Tools->>Obs: Query metrics/logs/traces/Kafka/DB
    Obs-->>Tools: Sanitized evidence
    Tools-->>LLM: Structured tool results
    LLM->>Tools: Optional knowledge search
    Tools->>RAG: Retrieve relevant runbooks/incidents
    RAG-->>Tools: Ranked source evidence
    Tools-->>LLM: Knowledge evidence
    LLM-->>INV: Grounded response
    INV->>Store: Persist session/evidence/tool history
    INV-->>API: Facts + interpretation + suggested checks
    API-->>UI: Render structured answer
    UI-->>Engineer: Evidence-first investigation
```

---

# 6. Incident Lifecycle Architecture

```mermaid
stateDiagram-v2
    [*] --> Open
    Open --> Acknowledged: acknowledge
    Acknowledged --> Investigating: owner starts investigation
    Investigating --> Mitigated: mitigation applied
    Mitigated --> Investigating: issue returns
    Mitigated --> Resolved: recovery verified
    Investigating --> Resolved: root cause fixed + recovery verified
    Resolved --> Closed: postmortem/review complete
    Resolved --> Investigating: regression/reopen
    Closed --> [*]
```

AI can recommend actions or draft summaries, but human-authorized operations control state transitions.

---

# 7. Signal-to-Incident Data Flow

```mermaid
flowchart LR
    PROM[Prometheus Alerts]
    LOG[Log Patterns]
    TR[Trace Errors/Latency]
    KF[Kafka Lag]
    DBE[DB/Pool State]
    HEALTH[Application Health]

    NORMALIZE[Signal Normalization\nPlanned]
    CORR[Correlation + Deduplication\nPlanned Day 13]
    INC[Incident]
    EVID[Incident Evidence]
    SLA[SLA / Escalation Engine]
    NOTIFY[Notification / Ticketing]
    INV[AI Investigation]

    PROM --> NORMALIZE
    LOG --> NORMALIZE
    TR --> NORMALIZE
    KF --> NORMALIZE
    DBE --> NORMALIZE
    HEALTH --> NORMALIZE

    NORMALIZE --> CORR
    CORR --> INC
    CORR --> EVID
    INC --> SLA
    INC --> INV
    SLA --> NOTIFY
    INV --> EVID
```

---

# 8. RAG / Knowledge Architecture

```mermaid
flowchart TD
    DOCS[Runbooks / Incidents / Architecture / Procedures]
    SAFE[Safe Document Reader + Secret Detection]
    CHUNK[Document Chunker]
    EMB[Embedding Model]
    VDB[(pgvector)]
    SEARCH[Knowledge Retrieval Service]
    CHAT[Knowledge Chat / AI Tool]
    USER[Engineer]

    DOCS --> SAFE
    SAFE --> CHUNK
    CHUNK --> EMB
    EMB --> VDB

    USER --> CHAT
    CHAT --> SEARCH
    SEARCH --> VDB
    VDB --> SEARCH
    SEARCH --> CHAT
    CHAT --> USER
```

The retrieval layer should return source metadata so the UI can expose evidence cards and source links.

---

# 9. Observability Architecture

```mermaid
flowchart LR
    APP[Demo / Monitored Services]

    APP -->|Micrometer /metrics| PROM[Prometheus]
    PROM --> GRAF[Grafana]

    APP -->|Structured Logs| FB[Filebeat]
    FB --> ES[Elasticsearch]
    ES --> KIB[Kibana]

    APP -->|OTLP| OTEL[OpenTelemetry Collector]
    OTEL --> JAEGER[Jaeger]

    APP -->|Events| KAFKA[Kafka]

    SUPPORT[Support Platform]
    SUPPORT --> PROM
    SUPPORT --> ES
    SUPPORT --> JAEGER
    SUPPORT --> KAFKA
    SUPPORT --> APP
```

---

# 10. Security Boundaries

```mermaid
flowchart TB
    USER[Authenticated User]
    IDP[OIDC Provider]
    UI[Web Console]
    API[Support API]
    RBAC[RBAC + App/Environment Policy]
    TOOLS[Read-only Tool Allowlist]
    REG[Registered Application Registry]
    TARGET[Approved Target Services]
    DATA[Telemetry / Knowledge]

    USER --> IDP
    IDP --> UI
    UI --> API
    API --> RBAC
    RBAC --> TOOLS
    TOOLS --> REG
    REG --> TARGET
    TARGET --> DATA
```

Security rules:

1. The model does not choose arbitrary network destinations.
2. Application URLs come from the verified application registry.
3. Tools are allowlisted.
4. Diagnostic operations remain read-only.
5. sensitive values are sanitized before model/UI exposure.
6. Day 14 adds user role plus application/environment authorization.
7. All human operational actions are audited.

---

# 11. External Integration Architecture — Planned

```mermaid
flowchart LR
    DOMAIN[Incident Domain]
    OUTBOX[(Integration Outbox)]
    WORKER[Integration Dispatcher]

    TPORT[Ticketing Port]
    NPORT[Notification Port]
    OPORT[On-call Port]

    JIRA[Jira Adapter]
    SN[ServiceNow Adapter]
    SLACK[Slack Adapter]
    TEAMS[Teams Adapter]
    PD[PagerDuty Adapter]

    DOMAIN --> OUTBOX
    OUTBOX --> WORKER
    WORKER --> TPORT
    WORKER --> NPORT
    WORKER --> OPORT

    TPORT --> JIRA
    TPORT --> SN
    NPORT --> SLACK
    NPORT --> TEAMS
    OPORT --> PD
```

Core incident persistence must not depend on external SaaS availability.

---

# 12. Deployment Topology

```mermaid
flowchart TB
    subgraph UserZone[User Zone]
        BROWSER[Browser]
    end

    subgraph Cluster[Kubernetes / OpenShift]
        ING[Ingress / Route]
        UI[Web Console Pod(s)]
        API[Support Platform Pod(s)]
        CFG[ConfigMap]
        SEC[Secret References]
    end

    subgraph DataZone[Data Services]
        PG[(PostgreSQL + pgvector)]
        KAFKA[(Kafka)]
        ES[(Elasticsearch)]
        PROM[(Prometheus)]
        JAEGER[(Jaeger)]
    end

    subgraph AIZone[AI Runtime]
        MODEL[Ollama / Configured LLM Endpoint]
        EMB[Embedding Model]
    end

    subgraph Apps[Monitored Applications]
        A1[Application A]
        A2[Application B]
        AN[Application N]
    end

    BROWSER --> ING
    ING --> UI
    UI --> API

    CFG --> API
    SEC --> API

    API --> PG
    API --> KAFKA
    API --> ES
    API --> PROM
    API --> JAEGER
    API --> MODEL
    API --> EMB

    API --> A1
    API --> A2
    API --> AN
```

---

# 13. High Availability Considerations

For production readiness:

- run multiple stateless support-platform replicas;
- externalize sessions/investigations to PostgreSQL;
- do not rely on in-memory telemetry for cross-replica history;
- configure database pool limits per replica;
- use readiness/liveness/startup probes;
- apply bounded retries/circuit breakers;
- protect AI/observability endpoints with rate limits;
- define retention for incidents, audits and investigation evidence.

---

# 14. Data Ownership

| Data | System of Record |
|---|---|
| Registered applications | PostgreSQL |
| Diagnostic configurations | PostgreSQL |
| Knowledge metadata | PostgreSQL |
| Embeddings | pgvector |
| Incident lifecycle | PostgreSQL — planned |
| Investigation history | PostgreSQL — planned |
| Metrics | Prometheus |
| Logs | Elasticsearch |
| Traces | Jaeger |
| Kafka offsets/lag | Kafka |
| AI model state | External/model runtime; do not treat as system of record |

---

# 15. End-to-End Operational Flow

```mermaid
flowchart LR
    A[Service Degrades]
    B[Telemetry Signal]
    C[Alert Correlation]
    D[Incident Created]
    E[Engineer Acknowledges]
    F[AI Investigation]
    G[Evidence Review]
    H[Runbook / Mitigation]
    I[Recovery Verification]
    J[Resolve]
    K[Postmortem]
    L[Knowledge Index]

    A --> B --> C --> D --> E --> F --> G --> H --> I --> J --> K --> L
    L -. future similar incident .-> F
```

This is the target product loop: every resolved incident should make the next incident easier to diagnose.
