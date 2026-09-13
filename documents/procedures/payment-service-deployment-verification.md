# Payment Service Deployment Verification Procedure

## Document Metadata
- **Application**: payment-service
- **Environment**: local
- **Type**: PROCEDURE
- **Version**: 1.0
- **Owner**: Release Management
- **Last Reviewed**: 2026-08-20

---

## 1. Post-Deployment Verification Steps
1. Verify `/support/info` responds with HTTP 200 and matches the expected release version.
2. Verify `/actuator/health` reports overall status `UP`.
3. Verify `/support/dependencies` confirms both `postgres-db` and `notification-service` are reachable.
4. Execute test transaction via `POST /api/payments/charge` with test token and verify HTTP 200 OK.
5. Confirm ring buffer `/support/errors` contains zero new exceptions within 5 minutes post-cutover.
