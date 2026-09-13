# Payment Service Architecture & System Overview

## Document Metadata
- **Application**: payment-service
- **Environment**: local
- **Type**: ARCHITECTURE
- **Version**: 2.0
- **Owner**: Core Payments Architecture Group
- **Last Reviewed**: 2026-08-15

---

## 1. System Purpose
The `payment-service` is a mission-critical Spring Boot microservice responsible for processing customer checkouts, authorizing transactions, tokenizing card numbers, and orchestrating downstream settlement notifications.

---

## 2. Architecture & Topography

```text
[ Web / Mobile Clients ]
           │
           ▼
[ API Gateway / Load Balancer ]
           │ (HTTP :8081)
           ▼
┌──────────────────────────────────────────────────────────┐
│                     payment-service                      │
│                                                          │
│  - PaymentController        - SupportStarter Agent       │
│  - PaymentProcessingService - HikariCP Pool (Max: 10)    │
│  - RecentErrorStore (Ring)  - DependencyHealthService    │
└──────────────┬───────────────────┬───────────────────────┘
               │                   │
               │ JDBC              │ HTTP REST
               ▼                   ▼
    ┌──────────────────────┐  ┌────────────────────────────┐
    │  PostgreSQL 16 DB    │  │   notification-service     │
    │  (Port 5432)         │  │   (Port 8082)              │
    │                      │  │                            │
    │  - payments table    │  │  - Order receipt emails    │
    │  - ledger entries    │  │  - SMS transaction alerts  │
    └──────────────────────┘  └────────────────────────────┘
               │
               │ HTTPS (Outbound)
               ▼
    ┌──────────────────────┐
    │ Stripe Payment GW    │
    │ (External Provider)  │
    └──────────────────────┘
```

---

## 3. Core Downstream Dependencies
1. **Primary Database (`postgres-db`)**:
   - Stores transaction records, ledger state, and idempotent transaction keys.
   - Connection URL: `jdbc:postgresql://localhost:5432/prod_support`
   - Failure impact: Critical. No new payments can be authorized or finalized without DB.
2. **Notification Service (`notification-service`)**:
   - Asynchronous notification dispatcher for email receipts and SMS confirmations.
   - Endpoint: `http://localhost:8082/api/notifications`
   - Failure impact: Medium. Payment succeeds; receipts queue for deferred retry.
3. **External Payment Gateway (`stripe-gateway`)**:
   - Outbound partner API for credit card clearing.
   - Failure impact: Critical. Circuit breaker trips to OPEN on consecutive timeouts > 2000ms.

---

## 4. Telemetry & Operational Endpoints
- Standardized operational metadata: `GET /support/info`
- Standardized health check: `GET /actuator/health`
- Sanitized recent exceptions: `GET /support/errors`
- Downstream dependency diagnostics: `GET /support/dependencies`
- Built-in simulation controllers: `/api/payments/simulate/*`
