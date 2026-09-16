# AI Production Support Platform (`prod-support-ai-platform`)

> **Day 7 Milestone: Safe Database Production Diagnostics + AI Tool Calling (HikariCP, PostgreSQL & Cross-System Correlation)**

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

# Day 6 — Add Kafka Production Diagnostics + AI Tool Calling

Day 6 adds safe, read-only **Apache Kafka production diagnostics** to the AI Production Support Platform. Support engineers and the Spring AI assistant can now diagnose event streaming backlogs, consumer health, and partition-level offsets, correlating live telemetry with approved RAG runbooks.

---

## 11. Kafka Fundamentals in Simple English

| Concept | Explanation | Real-World Analogy |
| :--- | :--- | :--- |
| **Apache Kafka** | Distributed event streaming platform designed for high-throughput, fault-tolerant publish-subscribe pipelines. | A central, durable conveyor belt system for messages. |
| **Topic** | A named stream/category to which events are published (e.g. `payment-events`). | An inbox tray designated for a specific business process. |
| **Partition** | An ordered, append-only sub-division of a topic allowing parallel read and write throughput. | Multiple parallel physical lanes in a highway toll plaza. |
| **Consumer Group** | A coordinated set of consumer instances cooperating to read events from a topic's partitions. | A team of cashiers dividing the checkout lanes among themselves. |
| **Committed Offset** | The sequential index of the last event successfully processed and confirmed by the consumer. | The last page number a reader wrote down as read. |
| **Latest Offset** | The sequential index of the latest event written by producers to the end of the partition log. | The total page count of the book so far. |
| **Consumer Lag** | The numerical difference between the latest offset and the committed offset: `Lag = Latest Offset - Committed Offset`. | The number of unread pages waiting to be read. |

### Concrete Example
```text
Kafka contains 100 messages.
Consumer processed 80 messages.

Latest Offset    = 100
Committed Offset = 80
Consumer Lag     = 20 messages
```

### Why High Lag Does NOT Automatically Mean Failure
* **Lag is an Observed Fact, Not Confirmed Failure**: Consumer lag measures rate variance between event production and event consumption.
* **Traffic Spikes**: A sudden burst of 10,000 orders creates temporary lag while consumers process steadily. If consumer state is `STABLE` and processing normally, lag is simply a healthy processing queue.
* **Persistent vs Transient**: A single point-in-time lag observation cannot prove whether lag is increasing, decreasing, or recovering. Multiple observations over time are required before declaring a production incident.

---

## 12. Day 6 Target Architecture

```text
                     Support Engineer
                            │
                            ▼
                   Investigation API
                            │
                            ▼
                      Spring AI
                            │
                          Ollama
                            │
                     Tool Selection
                            │
         ┌──────────────────┼─────────────────────┐
         │                  │                     │
         ▼                  ▼                     ▼
     Application          Kafka               Knowledge
     Diagnostics       Diagnostics               RAG
         │                  │                     │
    ┌────┼────┐       ┌─────┼────────┐            │
    ▼    ▼    ▼       ▼     ▼        ▼            ▼
 Health Errors Dep   Cluster Lag   Consumer    pgvector
                      Info         Group/Part
         │                  │                     │
         └──────────────────┼─────────────────────┘
                            ▼
                         Evidence
                            │
                            ▼
                          Ollama
                            │
                            ▼
                 Grounded Investigation
```

### Kafka Offset & Lag Collection Pipeline
```text
payment-service (Producer)
       │ (POST /api/payments)
       ▼
Kafka Topic: payment-events (Partitions 0..N)
       │
       ▼
payment-service (Consumer: payment-processing-group)
       │ (Committed Offsets stored in Kafka)
       ▼
support-platform
       │
       ▼ (AdminClient.listOffsets & listConsumerGroupOffsets)
Latest Offset - Committed Offset = Consumer Lag
       │
       ▼
KafkaEvidence (totalLag, highestLag, lagStatus, partitionLag)
       │
       ▼
Spring AI Grounded Diagnosis + RAG Runbook Guidance
```

---

## 13. Safety Guarantees & Security Boundaries

> [!IMPORTANT]
> **READ-ONLY KAFKA ACCESS**:
> All Kafka diagnostics use read-only Kafka `AdminClient` APIs (`describeCluster`, `describeConsumerGroups`, `listConsumerGroupOffsets`, `listOffsets`, `describeTopics`).
> The AI platform and AI tools **CANNOT**:
> - Publish messages
> - Consume business messages
> - Modify or reset consumer offsets
> - Reset or delete consumer groups
> - Create or delete topics or partitions
> - Alter topic configuration
> - Reprocess events or DLQs
> - Restart consumers or modify ACLs

> [!CAUTION]
> **SSRF & Network Boundary Protection**:
> The LLM and user can NEVER specify arbitrary `bootstrapServers` or unconfigured consumer groups. The platform resolves Kafka cluster credentials from the trusted application registry in PostgreSQL. Unconfigured consumer groups or topics are rejected at the security boundary.

---

## 14. Safe Read-Only Tool Allowlist

The active tool allowlist enforced by `ToolAllowlist` and registered with Spring AI contains 9 approved tools:

| Category | Tool Name | Description |
| :--- | :--- | :--- |
| **Application** | `get_application_info` | Metadata and registered configuration of the application |
| **Application** | `check_application_health` | Live actuator health and status (`UP`, `DOWN`) |
| **Application** | `get_recent_errors` | Recent error diagnostics from memory ring buffer |
| **Application** | `check_dependencies` | Downstream dependency health (database, HTTP APIs) |
| **Knowledge** | `search_knowledge_base` | Semantic vector search over runbooks, architecture, RCAs |
| **Kafka** | `check_kafka_cluster` | Live cluster connectivity, broker count, cluster ID |
| **Kafka** | `check_kafka_consumer_group` | Consumer group state (`STABLE`, `EMPTY`), member count, coordinator |
| **Kafka** | `check_kafka_consumer_lag` | Partition-level lag, total backlog, threshold classification |
| **Kafka** | `get_kafka_topic_info` | Topic partition count, leader IDs, ISR replica metadata |

---

## 15. Manual Verification Guide (PowerShell)

### 15.1 Start Local Infrastructure (PostgreSQL + Kafka KRaft)
```powershell
docker compose up -d
docker compose ps
```

### 15.2 Build Platform and Services
```powershell
mvn clean verify
```

### 15.3 Start Demo Services
In terminal 1 (payment-service):
```powershell
cd demo-apps/payment-service
mvn spring-boot:run
```

In terminal 2 (support-platform):
```powershell
cd support-platform
mvn spring-boot:run
```

### 15.4 Register Kafka Configuration for payment-service
```powershell
curl.exe -X PUT "http://localhost:8080/api/applications/1/kafka" `
  -H "Content-Type: application/json" `
  -d '{
    "enabled": true,
    "bootstrapServers": ["localhost:9092"],
    "consumerGroups": ["payment-processing-group"],
    "topics": ["payment-events"]
  }'
```

### 15.5 Check Kafka Diagnostics REST API
```powershell
curl.exe -X GET "http://localhost:8080/api/applications/1/diagnostics/kafka"
```

### 15.6 Simulate Consumer Lag (Pause Consumer & Publish Events)
```powershell
# Pause the payment consumer
curl.exe -X POST "http://localhost:8081/demo/fault/kafka-consumer/pause"

# Publish 20 payment events into Kafka
1..20 | ForEach-Object {
    $body = @{
        paymentId = "PAY-$_"
        amount = 1500
        currency = "INR"
    } | ConvertTo-Json

    Invoke-RestMethod -Method POST -Uri "http://localhost:8081/api/payments" -ContentType "application/json" -Body $body
}

# Verify backlog has accumulated
curl.exe -X GET "http://localhost:8080/api/applications/1/diagnostics/kafka/consumer-groups/payment-processing-group/lag"
```

### 15.7 Ask AI Support Assistant to Investigate Delay
```powershell
curl.exe -X POST "http://localhost:8080/api/support/investigate" `
  -H "Content-Type: application/json" `
  -d '{
    "applicationName": "payment-service",
    "environment": "local",
    "question": "Payment processing is delayed. Check Kafka and tell me what the runbook recommends."
  }'
```

### 15.8 Resume Consumer to Recover
```powershell
curl.exe -X POST "http://localhost:8081/demo/fault/kafka-consumer/resume"
```

---

## 16. Day 7 — Database Production Diagnostics

### Core Concepts Explained
- **What is a Connection Pool?**: A cache of reusable physical database connections maintained in memory by the application (using HikariCP in Spring Boot) to avoid the high overhead of establishing a new TCP/TLS handshake and authentication session for every query.
- **Active vs Idle Connections**:
  - **Active Connections**: Connections currently reserved by a thread and actively executing queries or participating in an open transaction.
  - **Idle Connections**: Ready, pre-warmed connections sitting in the pool awaiting work.
- **Pool Utilization**: The percentage of the configured maximum pool size currently occupied by active connections: `(activeConnections / maxPoolSize) * 100`.
- **Threads Awaiting Connection**: Application worker threads blocked and waiting to acquire a connection because all pool connections are in use. If `threadsAwaitingConnection > 0`, the application is experiencing connection pool starvation.
- **Why can a DB be UP while the application experiences DB errors?**: A database can be healthy, reachable, and responding in <10ms to a ping (`SELECT 1`), yet the application cannot acquire connections if the connection pool is saturated, queries are holding locks, or transactions are running long.
- **Long-Running Database Activity**: Database transactions or queries that remain open longer than a safe threshold (e.g. >5s), preventing connection return to the pool.
- **Fact vs Inference (Why high pool usage does NOT prove a connection leak)**:
  - *Observed Fact*: `active=10, max=10, waitingThreads=4`.
  - *Valid Inference*: The connection pool is under heavy pressure.
  - *Invalid Inference*: "There is a connection leak". A leak requires persistent saturation after traffic stops. Saturated pools are frequently caused by sudden traffic spikes, slow downstream APIs holding transactions open, or unindexed queries.

### Read-Only Security Model
- **Strictly READ-ONLY**: No generic SQL tools (`execute_sql`, `run_query`, `query_database`).
- **No LLM-supplied SQL**: Queries are hardcoded inside Java providers (`SELECT 1` for health, aggregate queries on `pg_stat_activity` without query text/parameters).
- **No Credential / Network Leakage**: The LLM accepts only `applicationName` and `environment`. It never passes or receives JDBC URLs, usernames, or passwords.
- **Safe Credential Resolution**: Passwords are resolved via `DatabaseCredentialProvider` (environment variables such as `DB_PAYMENT_PASSWORD`) and are never stored in plaintext or returned in API responses.

---

## 17. Day 7 Manual Windows Verification Steps

### 17.1 Start Infrastructure
```powershell
docker compose up -d
```

### 17.2 Configure Database for payment-service
```powershell
curl.exe -X PUT "http://localhost:8080/api/applications/1/database" `
  -H "Content-Type: application/json" `
  -d '{
    "databaseType": "POSTGRESQL",
    "displayName": "payment-db",
    "jdbcUrl": "jdbc:postgresql://localhost:5432/payment_db",
    "username": "payment_app",
    "credentialReference": "PAYMENT_DB",
    "enabled": true
  }'
```

### 17.3 Check Database Diagnostics REST API
```powershell
curl.exe -X GET "http://localhost:8080/api/applications/1/diagnostics/database"
```

### 17.4 Simulate Local Pool Pressure
```powershell
curl.exe -X POST "http://localhost:8081/demo/fault/database/pool-pressure?connections=8&seconds=20"
```

### 17.5 Investigate Database Connection Delay with Runbook
```powershell
curl.exe -X POST "http://localhost:8080/api/support/investigate" `
  -H "Content-Type: application/json" `
  -d '{
    "applicationName": "payment-service",
    "environment": "local",
    "question": "Why is payment-service experiencing database connection delays? Check the pool and runbook."
  }'
```

### 17.6 Clear Simulated Faults
```powershell
curl.exe -X POST "http://localhost:8081/demo/fault/database/clear"
```

---

## 18. Known Limitations
1. **Single Lag & Pool Measurement vs Trend**: A single snapshot indicates current utilization, not whether pool pressure is recovering or deteriorating.
2. **Oracle Diagnostics**: Interface `DatabaseDiagnosticProvider` is architecture-ready with `OracleDiagnosticProvider`, but PostgreSQL is the active implementation in Day 7.

---

## 19. Suggested Day 8 Objective

> Add centralized application log diagnostics using local Elasticsearch + Kibana, with safe structured log search tools such as `search_application_errors` and `get_error_pattern_summary`. Correlate logs with application health, Kafka, database diagnostics and RAG while preventing unrestricted log access and sensitive-data leakage.


