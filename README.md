# AI Production Support Platform (`prod-support-ai-platform`)

> **Day 4 Milestone: True Agentic AI Tool Calling with Spring AI & Ollama**

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
  * **Loop Limit & Isolated Timeouts**: Enforced per-investigation loop ceiling (`ai.tool-calling.max-tool-calls=5`) and tool timeout (`ai.diagnostic-timeout-seconds=3`). Failure or slowness of one tool does not abort the investigation.
  * **Safe Deterministic Fallback**: If Ollama is offline or fails, or if deterministic fallback is enabled, the platform executes safe deterministic diagnostics and synthesizes an answer without crashing.

---

## 2. Day 4 Agentic Tool Architecture

```text
+-------------------------------------------------------------------------------------------------------+
|                                           Support Engineer                                            |
+-------------------------------------------------------------------------------------------------------+
                                                    │
                 POST /api/support/investigate      │ GET /api/ai/status
                 POST /api/support/chat             │
                                                    ▼
+-------------------------------------------------------------------------------------------------------+
|                                     support-platform (Port 8080)                                      |
|                                                                                                       |
|  [Controllers]                                                                                        |
|    - SupportInvestigationController (/api/support/investigate)                                        |
|    - SupportChatController          (/api/support/chat - legacy Q&A)                                  |
|    - AiStatusController             (/api/ai/status)                                                  |
|    - ApplicationController          (/api/applications)                                               |
|                                                                                                       |
|  [Agentic Investigation Orchestration]                                                               |
|    - InvestigationService: Initializes InvestigationContext, configures OllamaOptions with tools,    |
|      invokes OllamaChatModel, extracts audited toolsUsed, gathers warnings & evidence.               |
|                                                                                                       |
|  [Spring AI Tool Registry & Callback Wrappers]                                                        |
|    - DiagnosticToolRegistry: Registers FunctionCallbackWrappers with Spring AI for:                   |
|        * get_application_info                                                                         |
|        * check_application_health                                                                     |
|        * get_recent_errors                                                                            |
|        * check_dependencies                                                                           |
|                                                                                                       |
|  [Security & Safety Layer]                                                                            |
|    - ToolAllowlist: Enforces only allowlisted diagnostic tools can be registered                      |
|    - ApplicationAccessValidator: Validates registered app exists, environment matches, app enabled    |
|    - Input Bounds: Error limit clamped between 1 and 50 (default 10)                                  |
|    - Infinite Loop Guard: InvestigationContext caps tool calls at maxToolCalls (default 5)            |
|    - ToolExecutionAuditor: Structured logging of tool execution, duration, status, parameters         |
|                                                                                                       |
|  [Diagnostic Service Client]                                                                          |
|    - DiagnosticService: Isolated HTTP RestClient with timeout (default 3s) and secret redaction       |
+-------------------+-----------------------------------+-----------------------------------------------+
                    │                                   │                               │
                    │ JDBC                              │ Read-Only HTTP GET            │ Function Calling
                    ▼                                   ▼                               ▼
         +--------------------+              +---------------------+         +----------------------+
         |    PostgreSQL      |              |   payment-service   |         |     Local Ollama     |
         |    (Port 5432)     |              |     (Port 8081)     |         |     (Port 11434)     |
         |                    |              |                     |         |                      |
         | registered_app     |              | - /support/info     |         | - llama3.1 / llama3.2|
         | table              |              | - /actuator/health  |         | - Function Call Loop |
         +--------------------+              | - /support/errors   |         +----------------------+
                                             | - /support/dep...   |
                                             +---------------------+
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

---

## 9. Local Setup & Execution Guide

### Prerequisites
* **Java**: JDK 21+ (Java 21 bytecode target)
* **Maven**: Apache Maven 3.8+ or 3.9+
* **Docker**: Docker Engine & Docker Compose (for PostgreSQL)
* **Ollama**: Installed locally on `http://localhost:11434` ([Download Ollama](https://ollama.com/download))
* **Ollama Model**: `llama3.1:latest`, `llama3.2`, or `mistral` supporting function calling

### Step 1: Start PostgreSQL via Docker Compose
From repository root:
```powershell
docker compose up -d
```

### Step 2: Start Ollama Model
```powershell
ollama pull llama3.1
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

## 10. Next Milestones (Day 5+)

* **RAG & Vector Knowledge Base**:
  * Ingest runbooks, historical postmortems, and architecture documents using pgvector in PostgreSQL.
  * Combine real-time agentic diagnostic tool execution with semantically retrieved operational runbooks.
* **Log Tailing & Stream Analysis**:
  * Real-time streaming analysis of exception patterns and log lines across distributed application instances.

