# Payment Service Troubleshooting Guide

## Document Metadata
- **Application**: payment-service
- **Environment**: local
- **Type**: TROUBLESHOOTING
- **Version**: 1.1
- **Owner**: Payments-Core Support
- **Last Reviewed**: 2026-09-01

---

## 1. Common Issues & Fast Diagnostic Checklist

### Symptom: High Rate of HTTP 503 Service Unavailable
1. Query `/support/dependencies`:
   - If `postgres-db` is `DOWN`, follow the [Payment Service Database Failure Runbook](file:///C:/ai_prod_support/prod-support-ai-platform/documents/runbooks/payment-service-database-failure.md).
   - If `stripe-gateway` is `DOWN`, verify outbound internet connectivity or upstream Stripe status page.
2. Query `/support/errors?limit=5`:
   - Inspect top exception class name and message.

### Symptom: Notification Failures After Payment
- If `notification-service` is unreachable, payments will still be captured successfully, but receipts will be delayed.
- Check `notification-service` logs and verify its port 8082 listener.
