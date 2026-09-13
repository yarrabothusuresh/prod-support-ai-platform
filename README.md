# AI Production Support Platform (`prod-support-ai-platform`)

> **Day 5 Milestone: Knowledge Base + RAG with PostgreSQL pgvector & Ollama Embeddings**

---

## 1. Project Purpose

The **AI Production Support Platform** is an enterprise-grade platform designed to onboard distributed applications and provide AI-assisted operational support, triage, and incident diagnostics.

* **Day 1 Foundation**:
  * **Decoupled Telemetry Starter**: A reusable Spring Boot starter (`support-agent-spring-boot-starter`) that microservices include to expose standardized operational metadata (`/support/info`) and Actuator metrics (`/actuator/health`).
  * **Onboarded Sample Microservice**: `payment-service` running on port 8081.
  * **Central Management Platform**: `support-platform` running on port 8080 with PostgreSQL persistence, Flyway schema migrations, and active connectivity probing.

* **Day 2 Operational Diagnostics & Grounded AI Context**:
  * **Sanitized In-Memory Error Store**: Reusable bounded ring-buffer (`RecentErrorStore`) in `support-agent-spring-boot-starter` capturing recent exceptions while automatically masking PII, passwords, bearer tokens, JDBC URLs, and truncating stack traces.
  * **Dependency Health Probing & Fault Injection**: Standardized `/support/dependencies` endpoint supporting health probing and real-time fault simulation overrides (`DependencyHealthService`).
  * **Deep Telemetry Ingestion in AI Platform**: `support-platform` dynamically ingests `/support/info`, `/actuator/health`, `/support/errors`, and `/support/dependencies`, feeding real ground-truth failure evidence into local Ollama prompts to eliminate hallucination.
  * **Interactive Incident Simulation**: `payment-service` exposes `/api/payments/simulate/error` and `/api/payments/simulate/dependency` for rapid operational verification.

* **Day 4 True Agentic AI Tool Calling**:
  * **Autonomous Tool Selection**: The LLM autonomously inspects support questions and dynamically decides *which* diagnostic tools to execute (`get_application_info`, `check_application_health`, `get_recent_errors`, `check_dependencies`).
  * **Safe & Strict Read-Only Boundaries**: Tools are 100% read-only and allowlisted. No arbitrary URL invocation (anti-SSRF), no DB mutations, no process/container restarts, no shell execution.
  * **Audit Logging & Tamper-Proof Metadata**: Every tool execution is audited (`AI_TOOL_EXECUTION tool=... application=... environment=... success=... durationMs=...`). `toolsUsed` is populated strictly from Java runtime execution metadata, never from LLM self-reporting.
  * **Loop Limit & Isolated Timeouts**: Enforced per-investigation loop ceiling (`ai.tool-calling.max-tool-calls=6`) and tool timeout (`ai.diagnostic-timeout-seconds=3`). Failure or slowness of one tool does not abort the investigation.
  * **Safe Deterministic Fallback**: If Ollama is offline or fails, or if deterministic fallback is enabled, the platform executes safe deterministic diagnostics and synthesizes an answer without crashing.

* **Day 5 Knowledge Base + RAG with PostgreSQL pgvector**:
  * **Local Vector Store with pgvector**: PostgreSQL 16 with `pgvector` extension enabled via Flyway migrations (`V2__enable_pgvector_and_create_knowledge_tables.sql`), storing 768-dimensional embeddings with HNSW cosine distance indexing and JSONB metadata GIN indexing.
  * **Dual Model Pipeline**: Separate models for Chat (`llama3:latest` / `qwen2.5`) and Embeddings (`nomic-embed-text`), avoiding context pollution and embedding mismatch.
  * **Document Ingestion & Chunking Engine**: Ingestion pipeline (`KnowledgeIngestionService`, `DocumentChunker`) with sliding window chunking (default 800 chars, 120 overlap), rich metadata stamping (`documentId`, `applicationName`, `environment`, `documentType`, `title`, `source`, `chunkNumber`, `version`, `owner`), content hashing (SHA-256) for deduplication, and automated secret scanning (`SecretDetector`).
  * **Strict Path Traversal Protection**: `SafeDocumentReader` enforces that only documents under the configured `./documents` base directory can be accessed.
  * **Unified Knowledge Q&A API**: Dedicated endpoint `POST /api/support/knowledge-chat` for grounded runbook, architecture, and incident Q&A with strict citations and anti-hallucination rules.
  * **5th AI Tool (`search_knowledge_base`)**: Strictly read-only tool registered in Spring AI allowlist. The LLM can autonomously invoke knowledge retrieval during live incident investigations to combine live runtime telemetry with runbook remediation steps.
  * **Historical Incident RCA Disclaimer**: Automated safety disclaimer attached whenever past incident RCAs or postmortems are retrieved, distinguishing past root causes from current live issues.

---

## 2. Day 5 Knowledge Base & Diagnostic Architecture

```text
+-----------------------------------------------------------------------------------------------------------------------+
|                                                   Support Engineer                                                    |
+-----------------------------------------------------------------------------------------------------------------------+
                                                            │
                         POST /api/support/investigate      │ POST /api/support/knowledge-chat
                         POST /api/knowledge/ingest-all     │ POST /api/knowledge/search
                                                            ▼
+-----------------------------------------------------------------------------------------------------------------------+
|                                             support-platform (Port 8080)                                              |
|                                                                                                                       |
|  [Controllers]                                                                                                        |
|    - SupportInvestigationController (/api/support/investigate - Combined Agentic Diagnosis & Runbook Guidance)        |
|    - SupportKnowledgeChatController (/api/support/knowledge-chat - Grounded Runbook / Architecture Q&A)               |
|    - KnowledgeController            (/api/knowledge/* - Document Ingestion, Semantic Search, Status)                   |
|    - SupportChatController          (/api/support/chat - Day 3 Rule-based Support Chat)                               |
|    - AiStatusController             (/api/ai/status)                                                                  |
|    - ApplicationController          (/api/applications)                                                               |
|                                                                                                                       |
|  [Knowledge Base & Ingestion Engine]                                                                                  |
|    - SafeDocumentReader: Base-directory validation, strict path traversal defense (blocks ../, \.., absolute escapes)  |
|    - SecretDetector: Pre-ingestion regex scanner (rejects passwords, bearer tokens, private keys, secrets)           |
|    - ContentHasher: SHA-256 deduplication and change detection                                                       |
|    - DocumentChunker: 800-char chunks, 120 overlap, metadata stamps (docId, app, env, type, title, source, chunkNo)  |
|    - KnowledgeRetrievalService: Similarity search, app/env scoping, document-type filtering, context capping          |
|    - SimilarIncidentService: Past incident RCA retrieval with automated historical safety disclaimer                  |
|                                                                                                                       |
|  [Spring AI Tool Registry & Callback Wrappers - 5 Approved Read-Only Tools]                                           |
|    1. get_application_info        - Microservice metadata, URLs, contact team                                          |
|    2. check_application_health    - Live Actuator health status                                                        |
|    3. get_recent_errors           - Live ring-buffer exceptions (PII masked)                                           |
|    4. check_dependencies          - Downstream dependencies & circuit breakers                                         |
|    5. search_knowledge_base       - Semantically searches approved runbooks, RCAs, architecture specs                  |
|                                                                                                                       |
|  [Security & Safety Boundaries]                                                                                       |
|    - ToolAllowlist: Enforces strictly approved 5 read-only tools                                                      |
|    - ApplicationAccessValidator: Anti-SSRF boundary check                                                             |
|    - Infinite Loop Guard: InvestigationContext caps diagnostic steps at maxToolCalls (default 6)                      |
|    - ToolExecutionAuditor: Audit logs recorded for every tool call with duration, parameters, and status              |
+-------------------+---------------------------------------+-----------------------------------+-----------------------+
                    │                                       │                                   │
                    │ JDBC                                  │ Read-Only HTTP GET                │ HTTP REST
                    ▼                                       ▼                                   ▼
         +--------------------+                  +---------------------+             +----------------------+
         |    PostgreSQL      |                  |   payment-service   |             |     Local Ollama     |
         |    (Port 5432)     |                  |     (Port 8081)     |             |     (Port 11434)     |
         |                    |                  |                     |             |                      |
         | - registered_app   |                  | - /support/info     |             | Chat:                |
         | - knowledge_doc    |                  | - /actuator/health  |             |  - llama3:latest     |
         | - vector_store     |                  | - /support/errors   |             | Embedding:           |
         |   (pgvector, HNSW) |                  | - /support/dep...   |             |  - nomic-embed-text  |
         +--------------------+                  +---------------------+             +----------------------+
```

---

## 2.1 Live Diagnostics vs. Knowledge Base

In production support, an effective AI platform must combine **real-time dynamic facts** with **curated institutional knowledge**:

| Feature / Dimension | Live Diagnostics (Days 1–4) | Knowledge Base / RAG (Day 5) |
| :--- | :--- | :--- |
| **Source of Truth** | Running microservice runtime memory & metrics | Version-controlled markdown documents (`./documents`) |
| **Data Freshness** | Milliseconds (live HTTP probes) | Static / updated on document ingestion |
| **Content Type** | Actuator status, active errors, downstream probes | Runbooks, incident RCAs, architecture specs, procedures |
| **Retrieval Method** | Spring AI Function Calling (`get_recent_errors`, etc.) | Vector similarity search (`pgvector` HNSW cosine distance) |
| **Primary Value** | Answers: *"What is failing right now?"* | Answers: *"How do we fix this according to standard procedure?"* |
| **Risk Mitigation** | Strict read-only tools, no mutations, timeout isolation | Anti-hallucination prompts, strict citation matching, RCA disclaimer |

---

## 2.2 Core RAG & Vector Concepts

* **RAG (Retrieval-Augmented Generation)**: Rather than relying on static model weights (which hallucinate or have stale knowledge), RAG dynamically queries a database for relevant context chunks and injects them into the LLM prompt at inference time.
* **Embeddings (`nomic-embed-text`)**: Converts text into dense 768-dimensional numerical vectors capturing semantic meaning. Sentences with similar meanings produce vectors close together in geometric space.
* **pgvector**: A PostgreSQL extension providing native `vector` data types and fast approximate nearest neighbor (ANN) indexing (e.g. HNSW or IVFFlat) using cosine or Euclidean distance.
* **Sliding Window Chunking**: Large documents are divided into overlapping chunks (e.g., 800 characters with 120-character overlap) so context is not broken across chunk boundaries.
* **Anti-Hallucination Guardrails**: Prompts explicitly forbid inventing steps not found in the retrieved documents and require returning exact source document titles and paths.----------------+
```

---

## 3. Monorepo Structure

```text
prod-support-ai-platform/
│
├── support-agent-spring-boot-starter/
│   └── Reusable Spring Boot starter for onboarded services (AI-agnostic)
│       ├── RecentErrorStore (in-memory ring buffer with secret masking)
│       └── DependencyHealthService (dependency probing & fault simulation)
│
├── demo-apps/
│   └── payment-service/
│       └── Demo service running on port 8081 (embeds starter)
│
├── support-platform/
│   ├── src/main/java/com/example/prodsupport/
│   │   ├── ai/
│   │   │   ├── client/       (SupportAiClient, OllamaSupportAiClient)
│   │   │   ├── config/       (AiConfig, AiProperties)
│   │   │   ├── model/        (ApplicationSupportContext, SupportAiResult)
│   │   │   ├── prompt/       (SupportPromptBuilder - agentic & grounded prompts)
│   │   │   ├── service/      (ApplicationContextService)
│   │   │   └── tools/        (Spring AI Tool Calling)
│   │   │       ├── DiagnosticToolRegistry.java
│   │   │       ├── ApplicationInfoAiTool.java     (get_application_info)
│   │   │       ├── HealthAiTool.java              (check_application_health)
│   │   │       ├── RecentErrorsAiTool.java        (get_recent_errors)
│   │   │       ├── DependencyAiTool.java          (check_dependencies)
│   │   │       ├── audit/
│   │   │       │   ├── ToolExecutionAudit.java
│   │   │       │   └── ToolExecutionAuditor.java
│   │   │       ├── context/
│   │   │       │   ├── InvestigationContext.java
│   │   │       │   └── InvestigationContextHolder.java
│   │   │       ├── model/                         (Requests, data payloads, DTOs)
│   │   │       └── security/
│   │   │           ├── ApplicationAccessValidator.java
│   │   │           └── ToolAllowlist.java
│   │   ├── application/
│   │   │   ├── controller/   (ApplicationController, AiStatusController, SupportChatController, SupportInvestigationController)
│   │   │   ├── dto/          (SupportInvestigationRequest, SupportInvestigationResponse, ...)
│   │   │   ├── entity/       (RegisteredApplication)
│   │   │   ├── repository/   (RegisteredApplicationRepository)
│   │   │   └── service/      (ApplicationService, SupportChatService, DiagnosticService, InvestigationService)
│   │   └── common/
│   │       └── exception/    (ApplicationDisabledException, GlobalExceptionHandler, ...)
│   └── Central production support platform on port 8080
│
├── docker-compose.yml
├── pom.xml
└── README.md
```

---

## 4. Agentic Tool Calling vs. Deterministic Investigation

| Feature | Deterministic Investigation (`/api/support/chat`) | Agentic Tool Calling (`/api/support/investigate`) |
| :--- | :--- | :--- |
| **Tool / Evidence Selection** | Predefined fixed pipeline: Always fetches all 4 endpoints upfront | Dynamic & autonomous: LLM inspects question and decides which tools to call |
| **Execution Order** | Static sequential HTTP calls | Multi-turn function calling loop governed by model reasoning |
| **Overhead** | Gathers telemetry for all endpoints even if question only asks about version | Minimizes network overhead by calling only relevant diagnostic tools |
| **Tamper Proofing** | Fixed gathered evidence | Audited tool execution tracking (`InvestigationContext`), anti-fabrication |
| **Loop Guard** | N/A (single-shot prompt) | Protected by `maxToolCalls=5` loop ceiling |
| **Failure Tolerance** | Best-effort fallback | Tool execution failures are returned to LLM as error results, allowing graceful diagnosis |

---

## 5. Security & Safety Principles

1. **100% Read-Only Diagnostics**:
   - Registered tools only perform HTTP GET queries to target services.
   - Absolutely no mutation, process restarting, database alterations, or OS command execution.
2. **Anti-SSRF Target Resolution**:
   - The LLM cannot provide arbitrary URLs. Tools accept `applicationName` and `environment`.
   - `ApplicationAccessValidator` resolves base URLs exclusively from the verified PostgreSQL `registered_app` registry.
3. **Application State Verification**:
   - Rejects disabled applications immediately with `400 Bad Request`.
   - Rejects unknown applications with `404 Not Found`.
4. **Secret & Sensitive Data Masking**:
   - Stack traces and error messages are sanitized before passing back to the LLM.
   - Passwords, bearer tokens, API keys, and authorization headers are masked with `[REDACTED]`.
5. **Infinite Loop Protection**:
   - `InvestigationContext` enforces a strict tool call budget (`ai.tool-calling.max-tool-calls=5`).
   - If the LLM exceeds the limit, further execution is halted and a diagnostic warning is appended.

---

## 6. End-to-End Verification Scenarios (PowerShell)

### Scenario 1: Health Diagnostic Tool
Investigate application health status when the service reports degraded health:
```powershell
# Inquire specifically about application health
curl.exe -X POST "http://localhost:8080/api/support/investigate" `
  -H "Content-Type: application/json" `
  -d '{"applicationName": "payment-service", "environment": "local", "userQuestion": "Is payment-service currently healthy and running?"}'
```
*Expected Tools Used*: `["check_application_health"]` (and optionally `get_application_info`)

### Scenario 2: Recent Errors Diagnostic Tool
Investigate recent application failures after simulating an error:
```powershell
# 1. Inject simulated timeout error into payment-service
curl.exe -X POST "http://localhost:8081/api/payments/simulate/error?type=DatabaseTimeoutException&message=Connection%20to%20postgres-db%20timed%20out%20after%203000ms&level=ERROR"

# 2. Ask the AI platform about recent errors
curl.exe -X POST "http://localhost:8080/api/support/investigate" `
  -H "Content-Type: application/json" `
  -d '{"applicationName": "payment-service", "environment": "local", "userQuestion": "Show me the recent errors and exceptions in payment-service."}'
```
*Expected Tools Used*: `["get_recent_errors"]`

### Scenario 3: Dependency Diagnostic Tool
Investigate downstream dependency failures:
```powershell
# 1. Simulate downstream database outage
curl.exe -X POST "http://localhost:8081/api/payments/simulate/dependency?dependency=postgres-db&status=DOWN"

# 2. Ask the AI platform to check dependencies
curl.exe -X POST "http://localhost:8080/api/support/investigate" `
  -H "Content-Type: application/json" `
  -d '{"applicationName": "payment-service", "environment": "local", "userQuestion": "Are any dependencies or downstream databases failing for payment-service?"}'

# 3. Clean up simulation override
curl.exe -X DELETE "http://localhost:8081/api/payments/simulate/dependency"
```
*Expected Tools Used*: `["check_dependencies"]`

### Scenario 4: Complete Multi-Tool Incident Investigation Flow
Perform a full triage when an incident is reported without specific subsystem indicators:
```powershell
# 1. Simulate both error and dependency failure
curl.exe -X POST "http://localhost:8081/api/payments/simulate/error?type=PaymentProcessingException&message=Failed%20to%20charge%20card&level=ERROR"
curl.exe -X POST "http://localhost:8081/api/payments/simulate/dependency?dependency=stripe-gateway&status=DOWN"

# 2. Ask an open-ended triage question
curl.exe -X POST "http://localhost:8080/api/support/investigate" `
  -H "Content-Type: application/json" `
  -d '{"applicationName": "payment-service", "environment": "local", "userQuestion": "Why is payment-service failing right now? Investigate the root cause."}'
```
*Expected Tools Used*: Multiple tools dynamically called (`check_application_health`, `get_recent_errors`, `check_dependencies`).

---

## 7. Automated Test Suite

All tests across all modules are **100% hermetic** and run without requiring an active Ollama process or live microservice:

```powershell
mvn test
```

### Monorepo Test Summary (69 Tests Passed)
* `support-agent-spring-boot-starter`: **13 tests**:
  * `RecentErrorStoreTest` (8 tests): Ring-buffer capacity, reverse-chronological retrieval, secret masking
  * `SupportDiagnosticsEndpointTest` (1 test): Diagnostics controller, `/support/errors`, `/support/dependencies`
  * `SupportInfoEndpointTest` (2 tests): Metadata endpoint & actuator health binding
  * `SupportPropertiesTest` (2 tests): Configuration properties binding & validation
* `demo-apps/payment-service`: **5 tests**:
  * Application context, payments API, `/support/info`, `/support/errors`, `/support/dependencies`, and incident simulation
* `support-platform`: **51 tests**:
  * `DiagnosticToolRegistrationTest` (4 tests): Tool names, descriptions, metadata, and tool allowlist enforcement
  * `DiagnosticToolExecutionTest` (7 tests): Tool invocation, limit clamping (1..50), unknown app rejection, disabled app rejection, timeout isolation, infinite loop threshold warning
  * `InvestigationServiceTest` (3 tests): Agentic tool-calling flow, deterministic fallback behavior, unregistered app validation
  * `SupportInvestigationControllerTest` (4 tests with `MockMvc`): HTTP 200 OK, validation errors 400, not found 404, disabled app 400
  * `SecuritySanitizationTest` (2 tests): Secret masking in error/diagnostic traces, arbitrary SSRF target rejection
  * `SupportPromptBuilderTest` (4 tests): Agentic prompts, anti-hallucination rules, system prompts
  * `ApplicationContextServiceTest` (5 tests with `MockWebServer`): Telemetry collection, partial failures, unreachable host
  * `OllamaSupportAiClientTest` (6 tests with `MockWebServer`): Availability ping, markdown-wrapped JSON, fallback parsing
  * `AiStatusControllerTest` (2 tests with `MockMvc`): Online/offline reporting
  * `SupportChatControllerTest` (4 tests with `MockMvc`): Chat endpoint operations
  * Plus Day 1 registration, unique constraint, Flyway, and connection probing tests.

### Optional Live Ollama Integration Test
To run the live integration test against a running local Ollama instance:
```powershell
mvn verify -Pollama-it -Dollama.it.enabled=true
```

---

## 8. Configuration Reference

All AI and diagnostic configurations are managed in `support-platform/src/main/resources/application.yml` and can be overridden via environment variables:

| Property | Default Value | Environment Variable | Description |
| :--- | :--- | :--- | :--- |
| `prod-support.ai.provider` | `ollama` | `AI_PROVIDER` | AI provider identifier |
| `prod-support.ai.model` | `llama3:latest` | `AI_MODEL` | Ollama model name to query |
| `prod-support.ai.base-url` | `http://localhost:11434` | `AI_BASE_URL` | Ollama server URL |
| `prod-support.ai.timeout-seconds` | `180` | `AI_TIMEOUT_SECONDS` | Timeout for AI model responses |
| `prod-support.ai.diagnostic-timeout-seconds` | `3` | `AI_DIAGNOSTIC_TIMEOUT` | Timeout when probing remote diagnostic endpoints |
| `prod-support.ai.max-tool-calls` | `5` | `AI_MAX_TOOL_CALLS` | Loop ceiling preventing runaway tool calling loops |
| `prod-support.ai.tool-calling.enabled` | `true` | `AI_TOOL_CALLING_ENABLED` | Enable agentic AI tool calling |
| `prod-support.ai.tool-calling.deterministic-fallback` | `false` | `AI_TOOL_CALLING_FALLBACK` | Safe deterministic fallback if AI fails |
| `prod-support.ai.log-prompt` | `true` | `AI_LOG_PROMPT` | Logs prompt and token usage metrics |
| `prod-support.knowledge.base-directory` | `./documents` | `KNOWLEDGE_BASE_DIR` | Directory containing approved operational markdown documents |
| `prod-support.knowledge.chunk-size` | `800` | `KNOWLEDGE_CHUNK_SIZE` | Maximum chunk character size for sliding window |
| `prod-support.knowledge.chunk-overlap` | `120` | `KNOWLEDGE_CHUNK_OVERLAP` | Character overlap between consecutive chunks |
| `prod-support.knowledge.max-context-chars` | `12000` | `KNOWLEDGE_MAX_CONTEXT_CHARS` | Upper bound for retrieved context injected into prompt |
| `prod-support.knowledge.default-top-k` | `5` | `KNOWLEDGE_DEFAULT_TOP_K` | Default number of chunks retrieved per query |
| `prod-support.knowledge.max-top-k` | `10` | `KNOWLEDGE_MAX_TOP_K` | Ceiling on requested top-K chunks |
| `prod-support.knowledge.embedding-model` | `nomic-embed-text` | `OLLAMA_EMBEDDING_MODEL` | Ollama embedding model name |
| `prod-support.knowledge.embedding-dimensions` | `768` | `EMBEDDING_DIMENSIONS` | Vector dimensions for pgvector column (768 for nomic-embed-text) |

---

## 9. Local Setup & Execution Guide

### Prerequisites
* **Java**: JDK 21+ (Java 21 bytecode target)
* **Maven**: Apache Maven 3.8+ or 3.9+
* **Docker**: Docker Engine & Docker Compose (PostgreSQL 16 with `pgvector/pgvector:pg16`)
* **Ollama**: Installed locally on `http://localhost:11434` ([Download Ollama](https://ollama.com/download))
* **Ollama Models**:
  * Chat Model: `ollama pull llama3:latest` (or `llama3.1`, `qwen2.5`)
  * Embedding Model: `ollama pull nomic-embed-text`

### Step 1: Start PostgreSQL with pgvector via Docker Compose
From repository root:
```powershell
docker compose up -d
```

### Step 2: Pull Ollama Models
```powershell
ollama pull llama3
ollama pull nomic-embed-text
```

### Step 3: Start `payment-service` (Port 8081)
In PowerShell Terminal 1:
```powershell
cd demo-apps/payment-service
mvn spring-boot:run
```

### Step 4: Start `support-platform` (Port 8080)
In PowerShell Terminal 2:
```powershell
cd support-platform
mvn spring-boot:run
```

### Step 5: Onboard Application
```powershell
curl.exe -X POST "http://localhost:8080/api/applications/onboard" `
  -H "Content-Type: application/json" `
  -d '{"applicationName": "payment-service", "environment": "local", "baseUrl": "http://localhost:8081", "contactTeam": "Payments-Core"}'
```

---

## 10. Day 5 PowerShell Verification Commands

### 10.1 Trigger Bulk Knowledge Base Ingestion
Ingests all markdown documents in `./documents`, performs secret scanning, deduplicates via SHA-256 content hashing, splits into 800-char chunks, and indexes into pgvector:
```powershell
curl.exe -X POST "http://localhost:8080/api/knowledge/ingest-all" `
  -H "Content-Type: application/json"
```

### 10.2 Check Knowledge Base Status
```powershell
curl.exe -X GET "http://localhost:8080/api/knowledge/status"
```

### 10.3 Semantic Knowledge Base Search
Search indexed runbooks and architecture documents:
```powershell
curl.exe -X POST "http://localhost:8080/api/knowledge/search" `
  -H "Content-Type: application/json" `
  -d '{
    "applicationName": "payment-service",
    "environment": "local",
    "query": "How to resolve database connection pool exhaustion?",
    "topK": 3
  }'
```

### 10.4 Dedicated Knowledge Chat Q&A
Ask operational questions answered strictly from approved runbooks with citations:
```powershell
curl.exe -X POST "http://localhost:8080/api/support/knowledge-chat" `
  -H "Content-Type: application/json" `
  -d '{
    "applicationName": "payment-service",
    "environment": "local",
    "question": "What is the standard runbook procedure when payment-service database connection pool is exhausted?"
  }'
```

### 10.5 Historical Incident RCA Search with Safety Disclaimer
Search past incidents to retrieve historical postmortem context with the automated safety disclaimer:
```powershell
curl.exe -X POST "http://localhost:8080/api/knowledge/search" `
  -H "Content-Type: application/json" `
  -d '{
    "applicationName": "payment-service",
    "environment": "local",
    "query": "payment gateway timeout incident RCA",
    "documentTypes": ["INCIDENT", "RCA"],
    "topK": 2
  }'
```

### 10.6 Combined Agentic Investigation (Live Facts + Runbook Guidance)
Simulate a live failure, then trigger an investigation. The LLM invokes diagnostic tools (e.g. `check_application_health`, `get_recent_errors`) AND `search_knowledge_base` to deliver a response with live facts, root causes, and runbook remediation steps:
```powershell
# Simulate DB error in payment-service
curl.exe -X POST "http://localhost:8081/api/payments/simulate/error?type=database"

# Run Agentic Investigation
curl.exe -X POST "http://localhost:8080/api/support/investigate" `
  -H "Content-Type: application/json" `
  -d '{
    "applicationName": "payment-service",
    "environment": "local",
    "question": "Why is payment-service failing and what does the runbook advise we do?"
  }'
```

---

## 11. Next Milestones (Day 6+)

* **Event-Driven Log & Alert Streaming via Kafka**:
  * Real-time streaming ingestion of error events and alert webhooks.
  * Automated triage trigger upon high-severity alert thresholds.
* **Proactive Anomaly Detection**:
  * Rolling metric anomaly evaluation triggering autonomous proactive investigations before user impact.

