# Follow-Up Recheck of Previously Reported Issues

Date: 2026-04-19

## Scope and Boundary

- Rechecked all previously reported follow-up issues from `.tmp/delivery_acceptance_architecture_audit.md` and prior follow-up reports.
- Static analysis only.
- Did not start the project, run tests, run Docker, or perform browser/manual runtime verification.
- Conclusions below are limited to what is provable from current repository contents.

## Summary

- Fixed: 6
- Partially Fixed: 0
- Not Fixed: 0

## Issue-by-Issue Verification

### 1. Login API exempt from request-signing enforcement
- Status: Fixed
- Rationale: `/api/auth/login` is no longer excluded from signing filter coverage, login tests now use signed unauthenticated requests, unsigned login is explicitly asserted as `400`, and frontend login sends signing headers.
- Evidence:
  - `meridianmart/src/main/java/com/meridianmart/security/RequestSigningFilter.java:48`
  - `meridianmart/src/main/java/com/meridianmart/security/RequestSigningFilter.java:56`
  - `meridianmart/src/test/java/com/meridianmart/integration/AuthControllerTest.java:16`
  - `meridianmart/src/test/java/com/meridianmart/integration/AuthControllerTest.java:44`
  - `meridianmart/src/main/resources/static/js/app.js:128`

### 2. Idempotency handling race-prone under concurrency
- Status: Fixed
- Rationale: Checkout and payment flows now perform pre-checks, lock acquisition, retry-based fallback idempotency lookups on lock contention, and return existing transaction/order when found. If another writer is still in-flight and no row is yet visible, paths now fail with explicit `503 in-progress` instead of ambiguous duplicate creation behavior.
- Evidence:
  - `meridianmart/src/main/java/com/meridianmart/service/OrderService.java:61`
  - `meridianmart/src/main/java/com/meridianmart/service/OrderService.java:64`
  - `meridianmart/src/main/java/com/meridianmart/service/OrderService.java:78`
  - `meridianmart/src/main/java/com/meridianmart/payment/PaymentService.java:35`
  - `meridianmart/src/main/java/com/meridianmart/payment/PaymentService.java:46`
  - `meridianmart/src/main/java/com/meridianmart/payment/PaymentService.java:54`
  - `meridianmart/src/test/java/com/meridianmart/unit/PaymentServiceTest.java:104`
  - `meridianmart/src/test/java/com/meridianmart/unit/PaymentServiceTest.java:123`

### 3. Pre-authorization declared but not implemented as executable flow
- Status: Fixed
- Rationale: Dedicated pre-authorization request DTO and API endpoint exist, service logic persists `PRE_AUTHORIZED` transaction records, and integration tests validate endpoint behavior.
- Evidence:
  - `meridianmart/src/main/java/com/meridianmart/dto/PreAuthorizeRequest.java:8`
  - `meridianmart/src/main/java/com/meridianmart/controller/PaymentController.java:66`
  - `meridianmart/src/main/java/com/meridianmart/payment/PaymentService.java:258`
  - `meridianmart/src/test/java/com/meridianmart/integration/PaymentControllerTest.java:151`

### 4. “Open disputes” retention rule approximated by order status
- Status: Fixed
- Rationale: Retention now depends on a dedicated `disputes` model/repository query for `OPEN` disputes instead of deriving dispute state from order statuses.
- Evidence:
  - `meridianmart/src/main/java/com/meridianmart/model/Dispute.java:12`
  - `meridianmart/src/main/java/com/meridianmart/repository/DisputeRepository.java:13`
  - `meridianmart/src/main/java/com/meridianmart/scheduler/DataRetentionScheduler.java:23`
  - `meridianmart/src/main/java/com/meridianmart/scheduler/DataRetentionScheduler.java:34`
  - `meridianmart/db/init.sql:219`

### 5. Nonce cleanup cadence too coarse versus replay window
- Status: Fixed
- Rationale: Nonce cleanup now runs every 300 seconds and removes nonces older than 300 seconds, aligned with the 5-minute signing replay window; nonce reuse is explicitly covered by integration test.
- Evidence:
  - `meridianmart/src/main/java/com/meridianmart/security/RequestSigningFilter.java:42`
  - `meridianmart/src/main/java/com/meridianmart/scheduler/RecommendationScheduler.java:33`
  - `meridianmart/src/main/java/com/meridianmart/scheduler/RecommendationScheduler.java:35`
  - `meridianmart/src/test/java/com/meridianmart/integration/SecurityTest.java:78`

### 6. High-risk payment API surface lacked request-level integration tests
- Status: Fixed
- Rationale: Integration suite now covers success paths, unauthorized/forbidden behavior, not-found handling, idempotent duplicate behavior, cross-store isolation, and pre-authorization endpoint coverage.
- Evidence:
  - `meridianmart/src/test/java/com/meridianmart/integration/PaymentControllerTest.java:57`
  - `meridianmart/src/test/java/com/meridianmart/integration/PaymentControllerTest.java:70`
  - `meridianmart/src/test/java/com/meridianmart/integration/PaymentControllerTest.java:80`
  - `meridianmart/src/test/java/com/meridianmart/integration/PaymentControllerTest.java:90`
  - `meridianmart/src/test/java/com/meridianmart/integration/PaymentControllerTest.java:122`
  - `meridianmart/src/test/java/com/meridianmart/integration/PaymentControllerTest.java:141`
  - `meridianmart/src/test/java/com/meridianmart/integration/PaymentControllerTest.java:151`

## Final Note

All previously tracked follow-up issues are now statically verifiable as fixed in the current repository snapshot.
