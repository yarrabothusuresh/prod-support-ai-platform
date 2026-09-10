# AI Production Support Platform (`prod-support-ai-platform`)

> **Day 2 Milestone: Local AI with Ollama + Spring AI for Operational Support**

---

## 1. Project Purpose

The **AI Production Support Platform** is an enterprise-grade platform designed to onboard distributed applications and provide AI-assisted operational support, triage, and incident diagnostics.

* **Day 1 Foundation**:
  * **Decoupled Telemetry Starter**: A reusable Spring Boot starter (`support-agent-spring-boot-starter`) that microservices include to expose standardized operational metadata (`/support/info`) and Actuator metrics (`/actuator/health`).
  * **Onboarded Sample Microservice**: `payment-service` running on port 8081.
  * **Central Management Platform**: `support-platform` running on port 8080 with PostgreSQL persistence, Flyway schema migrations, and active connectivity probing.

* **Operational Diagnostics & Grounded AI Context**:
  * **Sanitized In-Memory Error Store**: Reusable bounded ring-buffer (`RecentErrorStore`) in `support-agent-spring-boot-starter` capturing recent exceptions while automatically masking PII, passwords, bearer tokens, JDBC URLs, and truncating stack traces.
  * **Dependency Health Probing & Fault Injection**: Standardized `/support/dependencies` endpoint supporting health probing and real-time fault simulation overrides (`DependencyHealthService`).
  * **Deep Telemetry Ingestion in AI Platform**: `support-platform` dynamically ingests `/support/info`, `/actuator/health`, `/support/errors`, and `/support/dependencies`, feeding real ground-truth failure evidence into local Ollama prompts to eliminate hallucination.
  * **Interactive Incident Simulation**: `payment-service` exposes `/api/payments/simulate/error` and `/api/payments/simulate/dependency` for rapid operational verification.

---

## 2. Day 2 Architecture

```
+-----------------------------------------------------------------------------------+
|                                  Support Engineer                                 |
+-----------------------------------------------------------------------------------+
                                         │
                   POST /api/support/chat│ GET /api/ai/status
                                         ▼
+-----------------------------------------------------------------------------------+
|                        support-platform (Port 8080)                               |
|                                                                                   |
|  [Controllers]                                                                    |
|    - AiStatusController       (/api/ai/status)                                    |
|    - SupportChatController    (/api/support/chat)                                 |
|    - ApplicationController    (/api/applications)                                 |
|                                                                                   |
|  [Services & Business Logic]                                                      |
|    - SupportChatService       (Orchestrates lookup -> telemetry -> AI invocation) |
|    - ApplicationContextService(Fetches /support/info + /actuator/health)          |
|    - SupportPromptBuilder     (Constructs grounded, anti-hallucination prompt)    |
|    - OllamaSupportAiClient    (Pings /api/version, calls ChatModel, parses JSON)  |
|                                                                                   |
|  [Spring AI Layer]                                                                |
|    - OllamaChatModel / OllamaApi                                                  |
+-----------------------------------+-----------------------------------------------+
         │                          │                               │
         │ JDBC                     │ HTTP GET (Telemetry)          │ HTTP POST (Chat)
         ▼                          ▼                               ▼
+--------------------+   +---------------------+   +--------------------------------+
|    PostgreSQL      |   |   payment-service   |   |        Local Ollama            |
|    (Port 5432)     |   |     (Port 8081)     |   |        (Port 11434)            |
|                    |   |                     |   |                                |
| registered_app     |   | - /support/info     |   | - llama3:latest (or llama3.2)  |
| table              |   | - /actuator/health  |   | - GET  /api/version            |
+--------------------+   +---------------------+   | - POST /api/chat               |
                                                   +--------------------------------+
```

---

## 3. Monorepo Structure

```text
prod-support-ai-platform/
│
├── support-agent-spring-boot-starter/
│   └── Reusable Spring Boot starter for onboarded services (AI-agnostic)
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
│   │   │   ├── prompt/       (SupportPromptBuilder)
│   │   │   └── service/      (ApplicationContextService)
│   │   ├── application/
│   │   │   ├── controller/   (ApplicationController, AiStatusController, SupportChatController)
│   │   │   ├── dto/          (SupportChatRequest, SupportChatResponse, AiStatusResponse, ...)
│   │   │   ├── entity/       (RegisteredApplication)
│   │   │   ├── repository/   (RegisteredApplicationRepository)
│   │   │   └── service/      (ApplicationService, SupportChatService)
│   │   └── common/
│   │       └── exception/    (AiServiceUnavailableException, GlobalExceptionHandler, ...)
│   └── Central production support platform on port 8080
│
├── docker-compose.yml
├── pom.xml
└── README.md
```

---

## 4. Prerequisites

* **Java**: JDK 21+ (Java 21 bytecode target)
* **Maven**: Apache Maven 3.8+ or 3.9+
* **Docker**: Docker Engine & Docker Compose (for PostgreSQL)
* **Ollama**: Installed locally on `http://localhost:11434` ([Download Ollama](https://ollama.com/download))
* **Ollama Model**: `llama3:latest` (or `llama3.2`, `mistral`, `qwen2.5:0.5b` for resource-constrained systems)
* **Shell**: Windows PowerShell (examples below formatted for PowerShell)

---

## 5. Configuration Reference

All AI configurations are managed in `support-platform/src/main/resources/application.yml` and can be overridden via environment variables:

| Property | Default Value | Environment Variable | Description |
| :--- | :--- | :--- | :--- |
| `prod-support.ai.provider` | `ollama` | `AI_PROVIDER` | AI provider identifier |
| `prod-support.ai.model` | `llama3:latest` | `AI_MODEL` | Ollama model name to query |
| `prod-support.ai.base-url` | `http://localhost:11434` | `AI_BASE_URL` | Ollama server URL |
| `prod-support.ai.timeout-seconds` | `180` | `AI_TIMEOUT_SECONDS` | Timeout for AI model responses |
| `prod-support.ai.log-prompt` | `true` | `AI_LOG_PROMPT` | Logs redacted prompt & token stats |
| `prod-support.ai.context-timeout-seconds` | `3` | `AI_CONTEXT_TIMEOUT` | Timeout when probing target apps |

---

## 6. Getting Started & Setup Guide

### Step 1: Start and Pull Ollama Model

1. Verify Ollama is installed and running:
```powershell
ollama list
```

2. Pull the production support diagnostic model:
```powershell
ollama pull llama3:latest
```
*(Tip: For low-RAM machines, `ollama pull llama3.2:1b` or `ollama pull qwen2.5:0.5b` can be used by setting `$env:AI_MODEL="llama3.2:1b"`)*

---

### Step 2: Start PostgreSQL via Docker Compose

From the repository root (`prod-support-ai-platform`):

```powershell
docker compose up -d
docker compose ps
```

* Database: `prod_support`
* Port: `5432`

*(Note: For environments without Docker active, run `support-platform` with `-Dspring-boot.run.profiles=local-h2` or use `support-platform\run-local.bat`).*

---

### Step 3: Build Monorepo

Compile, package, and execute all 37 automated tests:

```powershell
mvn clean install
```

---

### Step 4: Start `payment-service` (Port 8081)

In PowerShell Terminal 1:

```powershell
cd demo-apps/payment-service
mvn spring-boot:run
```

Verify endpoints:
```powershell
curl.exe http://localhost:8081/support/info
curl.exe http://localhost:8081/actuator/health
```

---

### Step 5: Start `support-platform` (Port 8080)

In PowerShell Terminal 2:

```powershell
cd support-platform
mvn spring-boot:run
```

---

## 7. Verification & API Guide

### 1. Check AI Provider Status (`GET /api/ai/status`)

Checks if Ollama is running and reports model metadata without throwing a 500 error if offline:

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/ai/status" -Method Get
```

**When Ollama is online:**
```json
{
  "provider": "ollama",
  "model": "llama3:latest",
  "available": true
}
```

**When Ollama is offline:**
```json
{
  "provider": "ollama",
  "model": "llama3:latest",
  "available": false
}
```

---

### 2. Register `payment-service`

Register the demo microservice in the central registry:

```powershell
$body = @{
    applicationName = "payment-service"
    team = "payments"
    environment = "local"
    description = "Demo payment processing service"
    baseUrl = "http://localhost:8081"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/applications" -Method Post -ContentType "application/json" -Body $body
```

Response (`HTTP 201 Created`):
```json
{
  "id": 1,
  "applicationName": "payment-service",
  "team": "payments",
  "environment": "local",
  "description": "Demo payment processing service",
  "baseUrl": "http://localhost:8081",
  "healthUrl": "http://localhost:8081/actuator/health",
  "supportInfoUrl": "http://localhost:8081/support/info",
  "enabled": true
}
```

---

### 3. Ask AI Diagnostic Question (`POST /api/support/chat`)

Send a natural-language operational inquiry to the platform:

```powershell
$chatRequest = @{
    applicationName = "payment-service"
    environment = "local"
    question = "Users are reporting checkout payment failures. What is the current operational state of payment-service?"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/support/chat" -Method Post -ContentType "application/json" -Body $chatRequest | ConvertTo-Json -Depth 5
```

Live Grounded Response (`HTTP 200 OK`):
```json
{
  "applicationName": "payment-service",
  "environment": "local",
  "summary": "The payment service appears healthy based on the provided telemetry evidence.",
  "observedFacts": [
    "The Support Info Status is UP, indicating the endpoint is reachable.",
    "The Actuator Health Status is UP, indicating the endpoint is reachable."
  ],
  "possibleCauses": [
    "Insufficient information to determine potential causes of failures reported by users."
  ],
  "recommendedChecks": [
    "Verify the payment processing logic and database connections for any errors or issues.",
    "Check recent application logs for downstream gateway timeout errors."
  ],
  "confidence": "MEDIUM",
  "warnings": []
}
```

Notice how the AI strictly adheres to **`FACT != INFERENCE`**: it confirms the service is `UP` (observed fact) but explicitly refuses to speculate on internal failures (cautious inference), recommending further inspection.

---

### 4. Application Not Found Handling (`HTTP 404`)

When querying an unregistered application:

```powershell
$badApp = @{
    applicationName = "order-service"
    environment = "local"
    question = "Is this service running?"
} | ConvertTo-Json

try {
    Invoke-RestMethod -Uri "http://localhost:8080/api/support/chat" -Method Post -ContentType "application/json" -Body $badApp
} catch {
    $streamReader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
    $streamReader.ReadToEnd()
}
```

Response (`HTTP 404 Not Found`):
```json
{
  "timestamp": "2026-09-09T08:15:30.102+05:30",
  "status": 404,
  "error": "Not Found",
  "message": "Application 'order-service' in environment 'local' is not registered",
  "path": "/api/support/chat"
}
```

---

### 5. Partial Failure & Unreachable App Handling

If an onboarded service is registered but unreachable (e.g. crashed or network partitioned), `support-platform` accumulates non-blocking warnings and sends the partial context to the AI:

Response:
```json
{
  "applicationName": "unreachable-service",
  "environment": "local",
  "summary": "The application could not be reached via its support endpoints.",
  "observedFacts": [
    "Failed to reach /support/info: Connection refused",
    "Failed to reach /actuator/health: Connection refused"
  ],
  "possibleCauses": [
    "Service process is not running",
    "Port binding or firewall configuration issue"
  ],
  "recommendedChecks": [
    "Verify container/process status on host",
    "Inspect systemd or Docker container logs"
  ],
  "confidence": "HIGH",
  "warnings": [
    "Failed to retrieve /support/info from http://localhost:8099/support/info: Connection refused",
    "Failed to retrieve /actuator/health from http://localhost:8099/actuator/health: Connection refused"
  ]
}
```

---

### 6. Ollama Offline Handling (`HTTP 503`)

If Ollama is stopped or unreachable, `POST /api/support/chat` returns a clean, secure error without internal stack traces:

```json
{
  "timestamp": "2026-09-09T08:16:00.540+05:30",
  "status": 503,
  "error": "Service Unavailable",
  "message": "AI support service is currently unavailable. Please check that Ollama is running at http://localhost:11434.",
  "path": "/api/support/chat"
}
```

---

### 7. Interactive Incident Simulation & Grounded AI Verification

`payment-service` includes built-in incident simulation endpoints allowing you to test AI diagnostics:

```powershell
# 1. Inject a simulated database timeout error
curl -X POST "http://localhost:8081/api/payments/simulate/error?type=DatabaseTimeoutException&message=Connection%20to%20postgres-db%20timed%20out%20after%203000ms&level=ERROR"

# 2. Simulate downstream dependency outage
curl -X POST "http://localhost:8081/api/payments/simulate/dependency?dependency=postgres-db&status=DOWN"

# 3. Query the AI support assistant
curl -X POST http://localhost:8080/api/support/chat `
  -H "Content-Type: application/json" `
  -d '{"applicationName": "payment-service", "environment": "local", "userQuestion": "Why are payments failing?"}'

# 4. Clear simulated dependency outages
curl -X DELETE "http://localhost:8081/api/payments/simulate/dependency"
```

---

## 8. Automated Test Suite

All unit tests are **100% hermetic** and run without requiring an active Ollama process or live microservice:

```powershell
mvn test
```

### Monorepo Test Summary
* `support-agent-spring-boot-starter`: 13 tests:
  * `RecentErrorStoreTest` (8 tests): Ring-buffer capacity, reverse-chronological retrieval, PII/secret masking (passwords, bearer tokens, auth headers, JDBC credentials, credit cards, emails, stack trace suppression)
  * `SupportDiagnosticsEndpointTest` (1 test): Diagnostics controller, `/support/errors`, `/support/dependencies`, and override resolution
  * `SupportInfoEndpointTest` (2 tests): Metadata endpoint & actuator health binding
  * `SupportPropertiesTest` (2 tests): Configuration properties binding & validation
* `demo-apps/payment-service`: 5 tests:
  * Application context, payments API, `/support/info`, `/support/errors`, `/support/dependencies`, and incident simulation
* `support-platform`: 31 tests:
  * `SupportPromptBuilderTest` (4 tests): Anti-hallucination rules, telemetry, recent errors & dependency grounding
  * `ApplicationContextServiceTest` (5 tests with `MockWebServer`): 4-way telemetry collection, resilient partial failures, host unreachable
  * `OllamaSupportAiClientTest` (6 tests with `MockWebServer`): Availability ping, markdown-wrapped JSON, direct JSON, fallback parsing
  * `AiStatusControllerTest` (2 tests with `MockMvc`): Online / offline reachability reporting
  * `SupportChatControllerTest` (4 tests with `MockMvc`): Success 200, Validation 400, Not Found 404, Offline 503
  * Plus Day 1 registration, unique constraint, Flyway, and connection probing tests.

---

## 9. Next Milestones

* **Diagnostic AI Tools / Function Calling**:
  * Add the first diagnostic AI tools for application health and recent application logs.
  * Empower the AI support service to dynamically decide which read-only diagnostic tool to call based on the support engineer's question.
* **Log Ingestion & Analysis**:
  * Expose a bounded `/support/logs/recent` endpoint in `support-agent-spring-boot-starter` for live log tailing and exception pattern detection.
