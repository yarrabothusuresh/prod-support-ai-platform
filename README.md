# AI Production Support Platform (`prod-support-ai-platform`)

> **Day 1 Milestone: Foundation of a Reusable AI Production Support Platform**

---

## 1. Project Purpose

The **AI Production Support Platform** is an enterprise-grade platform designed to onboard distributed applications and provide AI-assisted operational support, triage, and incident diagnostics.

**Day 1** establishes the core architectural foundation:
* **Decoupled Telemetry Starter**: A reusable Spring Boot starter (`support-agent-spring-boot-starter`) that any service can include to expose standardized operational metadata (`/support/info`) without leaking business logic.
* **Onboarded Sample Microservice**: A demo payment service (`payment-service` on port 8081) that embeds the starter and exposes its health and operational metadata.
* **Central Management Platform**: A centralized registry platform (`support-platform` on port 8080) with PostgreSQL persistence, Flyway schema migrations, and active connectivity probing via Spring `RestClient`.

---

## 2. Day 1 Architecture

```
+-------------------------------------------------------+
|                    payment-service                    |
|                      (port 8081)                      |
|  - GET /api/payments/status                           |
|  - GET /actuator/health                               |
+-------------------------------------------------------+
                           │
                           │ embeds dependency
                           ▼
+-------------------------------------------------------+
|          support-agent-spring-boot-starter            |
|  - Conditional auto-configuration                     |
|  - Exposes GET /support/info                          |
|  - Dynamically integrates Actuator Health             |
+-------------------------------------------------------+
                           ▲
                           │ HTTP GET /support/info
                           │ (Probing & validation)
+-------------------------------------------------------+
|                   support-platform                    |
|                      (port 8080)                      |
|  - Application Registry REST APIs                     |
|  - Spring Data JPA + Flyway migrations                |
|  - Active RestClient connection testing               |
+-------------------------------------------------------+
                           │
                           │ JDBC / SQL
                           ▼
+-------------------------------------------------------+
|                  PostgreSQL Database                  |
|                      (port 5432)                      |
|  - Table: registered_application                      |
|  - Database: prod_support                             |
+-------------------------------------------------------+
```

---

## 3. Monorepo Structure

```text
prod-support-ai-platform/
│
├── support-agent-spring-boot-starter/
│   └── Reusable Spring Boot starter for onboarded services
│
├── demo-apps/
│   └── payment-service/
│       └── Demo service running on port 8081
│
├── support-platform/
│   └── Central production support platform on port 8080
│
├── docker/
│   └── Docker documentation and environment scripts
│
├── docs/
│   └── Architecture diagrams and design documentation
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
* **Shell**: Windows PowerShell (commands below formatted for PowerShell)

---

## 5. Getting Started & Setup Guide

### Step 1: Start PostgreSQL via Docker Compose

From the repository root (`prod-support-ai-platform`):

```powershell
docker compose up -d
```

To check database health:

```powershell
docker compose ps
```

* Database: `prod_support`
* User: `prod_support`
* Password: `prod_support`
* Port: `5432`
* Persistent volume: `postgres_data`

*(Note: For environments without Docker active, `support-platform` also includes an optional standalone profile `--spring.profiles.active=local-h2` for rapid development and testing).*

---

### Step 2: Build All Maven Modules

Compile, package, and run all unit and integration tests across the monorepo:

```powershell
mvn clean install
```

---

### Step 3: Start `payment-service` (Port 8081)

Open a new PowerShell terminal:

```powershell
cd demo-apps/payment-service
mvn spring-boot:run
```

Verify `payment-service` is up:

```powershell
curl.exe http://localhost:8081/api/payments/status
curl.exe http://localhost:8081/support/info
curl.exe http://localhost:8081/actuator/health
```

---

### Step 4: Start `support-platform` (Port 8080)

Open another PowerShell terminal:

```powershell
cd support-platform
mvn spring-boot:run
```

*(Or to run against the in-memory H2 PostgreSQL mode without Docker: `mvn spring-boot:run -Dspring-boot.run.profiles=local-h2`)*

---

## 6. Verification & API Guide

### 1. List Applications (Initial)

```powershell
curl.exe -s http://localhost:8080/api/applications
```

Response:
```json
[]
```

---

### 2. Register `payment-service`

Using PowerShell `Invoke-RestMethod` (Recommended for Windows):

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

Or using `curl.exe` with PowerShell stop-parsing token (`--%`):

```powershell
curl.exe --% -s -X POST http://localhost:8080/api/applications `
  -H "Content-Type: application/json" `
  -d "{\"applicationName\":\"payment-service\",\"team\":\"payments\",\"environment\":\"local\",\"description\":\"Demo payment processing service\",\"baseUrl\":\"http://localhost:8081\"}"
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
  "enabled": true,
  "createdAt": "2026-09-08T07:30:00.620458+05:30",
  "updatedAt": "2026-09-08T07:30:00.620458+05:30"
}
```

---

### 3. Duplicate Application Registration Conflict

Submitting the exact same `(applicationName, environment)` returns `HTTP 409 Conflict`:

```powershell
try {
    Invoke-RestMethod -Uri "http://localhost:8080/api/applications" -Method Post -ContentType "application/json" -Body $body
} catch {
    $streamReader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
    $streamReader.ReadToEnd()
}
```

Response (`HTTP 409 Conflict`):
```json
{
  "timestamp": "2026-09-08T07:30:27.4545841+05:30",
  "status": 409,
  "error": "Conflict",
  "message": "Application 'payment-service' is already registered for environment 'local'",
  "path": "/api/applications"
}
```

---

### 4. Test Connectivity to Onboarded Application

Triggers `support-platform` to actively probe `{baseUrl}/support/info` using Spring `RestClient`:

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/applications/1/test-connection" -Method Post
```

Success Response (`HTTP 200 OK`):
```json
{
  "connected": true,
  "applicationName": "payment-service",
  "status": "UP",
  "responseTimeMs": 42
}
```

---

### 5. Test Connectivity to Down / Unreachable Service

If an application is registered on a port that is unreachable:

```powershell
$downBody = @{
    applicationName = "unreachable-service"
    team = "payments"
    environment = "local"
    description = "Service that is down"
    baseUrl = "http://localhost:8099"
} | ConvertTo-Json

$downApp = Invoke-RestMethod -Uri "http://localhost:8080/api/applications" -Method Post -ContentType "application/json" -Body $downBody
Invoke-RestMethod -Uri "http://localhost:8080/api/applications/$($downApp.id)/test-connection" -Method Post
```

Failure Response (Clean error, no stack trace leaked):
```json
{
  "connected": false,
  "error": "Connection refused"
}
```

---

### 6. Delete Application

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/applications/1" -Method Delete
```

---

## 7. Automated Test Suite

Run the full automated test suite:

```powershell
mvn test
```

Test coverage includes:
1. **`SupportProperties` Binding**: Ensures configuration properties bind accurately and provide expected defaults.
2. **`/support/info` Endpoint**: Verifies metadata serialization and Actuator health integration.
3. **Application Registration**: Tests registration, persistence, and derived URLs.
4. **Duplicate Registration**: Verifies that unique constraint `(application_name, environment)` rejects duplicates with `409 Conflict`.
5. **Input Validation**: Tests Jakarta Bean Validation rejecting blank fields with `400 Bad Request`.
6. **Test Connection Success**: Verifies probing responsive microservices and calculating latency.
7. **Test Connection Failure**: Verifies graceful error capture without leaking stack traces.

---

## 8. Known Limitations (Day 1)

* **No AI Features Yet**: AI reasoning, log diagnosis, and incident triage are scheduled for Day 2+.
* **Registry Only**: Central platform currently maintains registration and live reachability testing.
* **Basic Auth / Security**: Endpoints are currently unauthenticated for local development.
* **Single Instance per Service**: Service discovery is point-to-point via `baseUrl` rather than dynamic service discovery (e.g. Eureka/Consul/K8s DNS).

---

## 9. Day 2 Roadmap

* **Ollama + Spring AI Integration**: Embed Spring AI into `support-platform` to query local LLMs (e.g. `llama3` or `mistral`).
* **First AI Diagnostic Action**: Allow `support-platform` to answer natural language operational inquiries using registered application metadata, health indicators, and status.
* **Log Ingestion Hook**: Expand `support-agent-spring-boot-starter` to collect recent error logs on demand.
