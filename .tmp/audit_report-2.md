# MeridianMart Delivery Acceptance and Project Architecture Static Audit

Date: 2026-04-19
Scope mode: Static-only (no runtime execution)

## 1. Verdict
- Overall conclusion: Partial Pass

## 2. Scope and Static Verification Boundary
- Reviewed:
  - Documentation, project manifests, configuration, and scripts: README.md:66, pom.xml:1, src/main/resources/application.yml:16, Dockerfile:39, scripts/backup.sh:5, scripts/restore.sh:1
  - Security/authz/authn chain and filters: src/main/java/com/meridianmart/config/SecurityConfig.java:34, src/main/java/com/meridianmart/security/JwtAuthenticationFilter.java:31, src/main/java/com/meridianmart/security/RateLimitingFilter.java:32, src/main/java/com/meridianmart/security/RequestSigningFilter.java:43
  - Core business modules: checkout/order/payment/recommendation/notifications/audit/config: src/main/java/com/meridianmart/service/OrderService.java:49, src/main/java/com/meridianmart/payment/PaymentService.java:30, src/main/java/com/meridianmart/recommendation/RecommendationService.java:56, src/main/java/com/meridianmart/service/NotificationService.java:26, src/main/java/com/meridianmart/audit/AuditService.java:18, src/main/java/com/meridianmart/service/AppConfigService.java:26
  - Data model/schema constraints: db/init.sql:70, db/init.sql:168, db/init.sql:176, db/init.sql:186
  - UI templates/static scripts for required interaction evidence: src/main/resources/templates/home.html:30, src/main/resources/templates/product.html:26, src/main/resources/templates/confirmation.html:26, src/main/resources/static/js/app.js:234
  - Test code (unit/integration/frontend/e2e presence and coverage shape): src/test/java/com/meridianmart/integration/BaseIntegrationTest.java:27, src/test/java/com/meridianmart/integration/SecurityTest.java:64, src/test/java/com/meridianmart/unit/RecommendationServiceTest.java:67, src/test/java/com/meridianmart/frontend/BaseSeleniumTest.java:23
- Not reviewed:
  - Runtime behavior, deployment health, network/browser execution, Docker health checks, cron execution, DB runtime migrations.
- Intentionally not executed:
  - Application start, Maven tests, Docker Compose, scripts.
- Claims requiring manual verification:
  - End-to-end runtime correctness, actual scheduler execution timing in deployed environment, browser-level UX rendering fidelity, concurrency race outcomes under production load.

## 3. Repository / Requirement Mapping Summary
- Prompt core goal mapped:
  - Offline shopper-staff-admin commerce loop with role-based security, recommendations, notifications, POS-confirmed payment/refund, governance/audit, request signing/rate limiting/brute-force lockout, backup/retention/anonymization.
- Main mapped implementation areas:
  - Authentication/security filters and policy: SecurityConfig + JWT + signing + rate limits.
  - Domain workflows: Cart/Favorites/Orders/Staff/Admin/Payments/Recommendations.
  - Governance: audit logs, config change history, masking/encryption, compliance report endpoint.
  - Durability/safety: idempotency keys, distributed lock table, schema constraints.
  - Tests: MockMvc integration + Mockito unit + Selenium suites present.

## 4. Section-by-section Review

### 4.1 Hard Gates

#### 4.1.1 Documentation and static verifiability
- Conclusion: Pass
- Rationale: Startup/test/config documentation is present and maps to concrete files and env vars; static verification is feasible.
- Evidence: README.md:66, README.md:75, README.md:78, README.md:89, .env.example:7, .env.example:8, .env.example:9, pom.xml:1, src/main/resources/application.yml:16
- Manual verification note: Runtime startup remains manual verification required.

#### 4.1.2 Material deviation from Prompt
- Conclusion: Partial Pass
- Rationale: Core scope is aligned, but request-signing design materially weakens prompt intent for signed API requests (see High issue #1), and behavior-event recording depends on client sequencing rather than atomic backend capture (see High issue #2).
- Evidence: src/main/java/com/meridianmart/security/RequestSigningFilter.java:117, src/main/resources/static/js/app.js:16, src/main/java/com/meridianmart/service/RatingService.java:31, src/main/resources/static/js/app.js:303
- Manual verification note: Security threat model impact depends on deployment assumptions.

### 4.2 Delivery Completeness

#### 4.2.1 Core explicit requirements coverage
- Conclusion: Partial Pass
- Rationale:
  - Covered: login/roles/cart/favorites/ratings/checkout+POS/receipt+12-hour timestamp/recommendations/notifications cap/refunds/admin flags/config/compliance/rate limit/brute force/request nonce window/audit/backup-retention/anonymization.
  - Partial: signed-request security model not implemented with configured secret for authenticated calls.
- Evidence:
  - Checkout + receipt + 12h format: src/main/java/com/meridianmart/service/OrderService.java:49, src/main/java/com/meridianmart/service/OrderService.java:46, src/main/resources/templates/confirmation.html:26
  - Stock warning: src/main/java/com/meridianmart/service/ProductService.java:92, src/main/resources/templates/product.html:42
  - Recommendations: src/main/java/com/meridianmart/recommendation/RecommendationService.java:147, src/main/java/com/meridianmart/recommendation/RecommendationService.java:177, src/main/java/com/meridianmart/recommendation/RecommendationService.java:195, src/main/java/com/meridianmart/scheduler/RecommendationScheduler.java:20
  - Notification cap: src/main/java/com/meridianmart/service/NotificationService.java:39, src/main/resources/application.yml:71
  - Payments/idempotency/locks: src/main/java/com/meridianmart/payment/PaymentService.java:33, src/main/java/com/meridianmart/payment/DistributedLockService.java:21, db/init.sql:168, db/init.sql:176
  - Request signing + replay window: src/main/java/com/meridianmart/security/RequestSigningFilter.java:83, src/main/java/com/meridianmart/security/RequestSigningFilter.java:135

#### 4.2.2 End-to-end deliverable vs partial demo
- Conclusion: Pass
- Rationale: Multi-module product-like structure, controllers/services/repositories/models/templates/scripts/tests are all present.
- Evidence: README.md:13, README.md:58, README.md:59, README.md:60, src/main/java/com/meridianmart/controller/OrderController.java:1, src/main/resources/templates/home.html:1, src/test/java/com/meridianmart/integration/BaseIntegrationTest.java:27

### 4.3 Engineering and Architecture Quality

#### 4.3.1 Structure and module decomposition
- Conclusion: Pass
- Rationale: Responsibilities are clearly separated by layer and concern; no monolithic single-file anti-pattern.
- Evidence: src/main/java/com/meridianmart/controller/OrderController.java:1, src/main/java/com/meridianmart/service/OrderService.java:1, src/main/java/com/meridianmart/repository/OrderRepository.java:1, src/main/java/com/meridianmart/recommendation/RecommendationService.java:1, src/main/java/com/meridianmart/payment/PaymentService.java:1

#### 4.3.2 Maintainability and extensibility
- Conclusion: Partial Pass
- Rationale: Good modularity and configuration-driven thresholds exist, but security-signing design and client-coupled behavior tracking reduce robustness and extensibility under multiple clients.
- Evidence: src/main/java/com/meridianmart/service/AppConfigService.java:71, src/main/java/com/meridianmart/recommendation/RecommendationService.java:52, src/main/java/com/meridianmart/security/RequestSigningFilter.java:117, src/main/resources/static/js/app.js:234

### 4.4 Engineering Details and Professionalism

#### 4.4.1 Error handling, logging, validation, API design
- Conclusion: Partial Pass
- Rationale: Validation and exception handling are broadly present; logging is structured. However, key security detail (signature keying) is weak.
- Evidence: src/main/java/com/meridianmart/config/GlobalExceptionHandler.java:28, src/main/java/com/meridianmart/dto/RatingRequest.java:14, src/main/resources/application.yml:80, src/main/java/com/meridianmart/service/OrderService.java:168, src/main/java/com/meridianmart/security/RequestSigningFilter.java:117

#### 4.4.2 Product vs demo quality
- Conclusion: Pass
- Rationale: Includes broad functional surface, governance modules, schema, scripts, and layered tests.
- Evidence: README.md:13, db/init.sql:1, scripts/backup.sh:1, src/main/java/com/meridianmart/controller/AdminController.java:26, src/test/java/com/meridianmart/integration/SecurityTest.java:16

### 4.5 Prompt Understanding and Requirement Fit

#### 4.5.1 Business goal and constraints fit
- Conclusion: Partial Pass
- Rationale: Business scenario is largely implemented, but two design choices weaken strict fit: non-secret signing key strategy and non-atomic backend capture of some behavior events.
- Evidence: src/main/java/com/meridianmart/security/RequestSigningFilter.java:117, src/main/resources/static/js/app.js:16, src/main/java/com/meridianmart/service/RatingService.java:56, src/main/resources/static/js/app.js:303

### 4.6 Aesthetics (frontend)

#### 4.6.1 Visual and interaction quality
- Conclusion: Pass
- Rationale: Functional pages are clearly separated; consistent styling and feedback states exist (alerts, warnings, badges, status chips, print view, responsive rules).
- Evidence: src/main/resources/templates/home.html:30, src/main/resources/templates/product.html:26, src/main/resources/templates/product.html:42, src/main/resources/static/css/main.css:57, src/main/resources/static/css/main.css:143, src/main/resources/static/css/main.css:246
- Manual verification note: Pixel-level rendering and real-device behavior are manual verification required.

## 5. Issues / Suggestions (Severity-Rated)

### [High] 1) Request-signing secret is configured but not used for authenticated signatures
- Conclusion: Fail
- Evidence:
  - Configured secret exists: src/main/resources/application.yml:76, .env.example:9
  - Filter stores secret but computes HMAC key from Bearer token instead: src/main/java/com/meridianmart/security/RequestSigningFilter.java:46, src/main/java/com/meridianmart/security/RequestSigningFilter.java:117
  - Client mirrors this by signing with JWT or empty string: src/main/resources/static/js/app.js:16
  - Tests encode this behavior: src/test/java/com/meridianmart/integration/BaseIntegrationTest.java:109
- Impact:
  - Signature security is not independently rooted in server-held secret for authenticated requests.
  - Unauthenticated signed requests (login) can be generated with empty key; replay controls remain, but signing does not add origin authenticity.
- Minimum actionable fix:
  - Use server-side secret material (or per-client server-managed secret) in signature verification; do not derive signing key from bearer token; disallow empty-key signature mode except explicit, tightly scoped bootstrap flow.

### [High] 2) Recommendation behavior capture for rating/favorite/add-to-cart/view is client-coupled, not transactionally enforced server-side
- Conclusion: Partial Fail
- Evidence:
  - Rating backend persists rating but no behavior event write: src/main/java/com/meridianmart/service/RatingService.java:31, src/main/java/com/meridianmart/service/RatingService.java:56
  - UI sends separate behavior calls after main actions: src/main/resources/static/js/app.js:234, src/main/resources/static/js/app.js:261, src/main/resources/static/js/app.js:274, src/main/resources/static/js/app.js:303
  - Collaborative filtering relies on behavior events: src/main/java/com/meridianmart/recommendation/RecommendationService.java:113
- Impact:
  - Event stream can become incomplete under client-side failures or alternate API clients, degrading recommendation fidelity and violating strict requirement semantics for recording behavior events.
- Minimum actionable fix:
  - Record behavior events in backend services (cart/favorite/rating and product view endpoint) within the same transaction as primary action; keep `/api/behavior` optional for supplemental telemetry.

### [Medium] 3) High-risk authorization coverage gaps remain in integration tests
- Conclusion: Partial Fail (testing dimension)
- Evidence:
  - Notification integration tests only cover list + cap; no mark-read authz/object-level negative cases: src/test/java/com/meridianmart/integration/NotificationControllerTest.java:30, src/test/java/com/meridianmart/integration/NotificationControllerTest.java:42
  - Order integration tests lack cross-store and ownership-negative checks for shopper order history/listing: src/test/java/com/meridianmart/integration/OrderControllerTest.java:50, src/test/java/com/meridianmart/integration/OrderControllerTest.java:94
  - Recommendation integration tests do not verify feature-flag off behavior: src/test/java/com/meridianmart/integration/RecommendationControllerTest.java:32, src/test/java/com/meridianmart/integration/RecommendationControllerTest.java:41
- Impact:
  - Severe authorization regressions could pass CI undetected.
- Minimum actionable fix:
  - Add integration tests for: notification mark-read non-owner/unauthorized, order-history isolation under cross-store/cross-user, recommendations disabled-by-flag path.

### [Low] 4) README workflow is Docker-centric, limiting static reproducibility paths outside containerized setup
- Conclusion: Partial
- Evidence: README.md:68, README.md:78, README.md:97, src/main/resources/application.yml:16
- Impact:
  - Reviewer/operator without Docker has no first-class documented path.
- Minimum actionable fix:
  - Add direct Maven + local MySQL/H2 verification instructions alongside Docker flow.

## 6. Security Review Summary

- Authentication entry points: Pass
  - Evidence: src/main/java/com/meridianmart/controller/AuthController.java:23, src/main/java/com/meridianmart/service/AuthService.java:38, src/main/java/com/meridianmart/service/AuthService.java:66, src/main/java/com/meridianmart/security/JwtAuthenticationFilter.java:31
- Route-level authorization: Pass
  - Evidence: src/main/java/com/meridianmart/config/SecurityConfig.java:40, src/main/java/com/meridianmart/config/SecurityConfig.java:61, src/main/java/com/meridianmart/config/SecurityConfig.java:67
- Object-level authorization: Partial Pass
  - Evidence: src/main/java/com/meridianmart/service/CartService.java:75, src/main/java/com/meridianmart/service/FavoriteService.java:54, src/main/java/com/meridianmart/service/NotificationService.java:63, src/main/java/com/meridianmart/service/OrderService.java:153
  - Note: Several critical object-level paths are covered, but test depth is uneven (see Issue #3).
- Function-level authorization: Pass
  - Evidence: src/main/java/com/meridianmart/controller/StaffController.java:19, src/main/java/com/meridianmart/controller/AdminController.java:27, src/main/java/com/meridianmart/controller/PaymentController.java:32
- Tenant/user data isolation: Partial Pass
  - Evidence: src/main/java/com/meridianmart/service/OrderService.java:205, src/main/java/com/meridianmart/controller/PaymentController.java:36, src/test/java/com/meridianmart/integration/PaymentControllerTest.java:141, src/test/java/com/meridianmart/integration/StaffControllerTest.java:107
  - Note: Core store isolation exists and has tests for staff/payment flows; shopper order/list isolation negative tests are limited.
- Admin/internal/debug protection: Pass
  - Evidence: src/main/java/com/meridianmart/config/SecurityConfig.java:42, src/main/java/com/meridianmart/config/SecurityConfig.java:61, src/main/java/com/meridianmart/controller/AdminController.java:27

## 7. Tests and Logging Review

- Unit tests: Pass
  - Evidence: src/test/java/com/meridianmart/unit/AuthServiceTest.java:31, src/test/java/com/meridianmart/unit/OrderServiceTest.java:25, src/test/java/com/meridianmart/unit/PaymentServiceTest.java:27, src/test/java/com/meridianmart/unit/RecommendationServiceTest.java:28
- API/integration tests: Partial Pass
  - Evidence: src/test/java/com/meridianmart/integration/BaseIntegrationTest.java:27, src/test/java/com/meridianmart/integration/SecurityTest.java:78, src/test/java/com/meridianmart/integration/PaymentControllerTest.java:162
  - Gap: uneven object-level/authz negatives on some endpoints (Issue #3).
- Logging categories/observability: Pass
  - Evidence: src/main/resources/application.yml:80, src/main/java/com/meridianmart/service/OrderService.java:168, src/main/java/com/meridianmart/audit/AuditService.java:27
- Sensitive-data leakage risk in logs/responses: Partial Pass
  - Positive evidence: src/test/java/com/meridianmart/integration/SecurityTest.java:51, src/main/java/com/meridianmart/service/AppConfigService.java:35, src/test/java/com/meridianmart/integration/AdminControllerTest.java:85
  - Residual risk: Manual verification required for full log corpus under failure/runtime conditions.

## 8. Test Coverage Assessment (Static Audit)

### 8.1 Test Overview
- Unit tests exist: Yes (Mockito/JUnit 5).
  - Evidence: src/test/java/com/meridianmart/unit/AuthServiceTest.java:28, src/test/java/com/meridianmart/unit/PaymentServiceTest.java:27
- API/integration tests exist: Yes (SpringBootTest + MockMvc).
  - Evidence: src/test/java/com/meridianmart/integration/BaseIntegrationTest.java:27, src/test/java/com/meridianmart/integration/BaseIntegrationTest.java:28
- Frontend/E2E tests exist: Yes (Selenium).
  - Evidence: src/test/java/com/meridianmart/frontend/BaseSeleniumTest.java:23, src/test/java/com/meridianmart/e2e/ShopperJourneyTest.java:1
- Test entry points/docs exist: Yes, Dockerized test runner documented.
  - Evidence: README.md:89, README.md:97, run_tests.sh:1

### 8.2 Coverage Mapping Table

| Requirement / Risk Point | Mapped Test Case(s) | Key Assertion / Fixture / Mock | Coverage Assessment | Gap | Minimum Test Addition |
|---|---|---|---|---|---|
| Login + brute-force lockout | src/test/java/com/meridianmart/integration/AuthControllerTest.java:52; src/test/java/com/meridianmart/unit/AuthServiceTest.java:88 | 423 lock assertions and ACCOUNT_LOCKED audit verification | sufficient | None material | Add timed unlock boundary test at exactly 15m |
| Request signing headers + replay nonce | src/test/java/com/meridianmart/integration/SecurityTest.java:78, src/test/java/com/meridianmart/integration/SecurityTest.java:92 | 400 on nonce reuse, 401 on invalid signature | basically covered | Does not assert configured signing secret path | Add tests proving secret-backed signing acceptance/rejection |
| Rate limiting 60 rpm | No direct threshold exhaustion test found | N/A | insufficient | No explicit 429 limit-trigger test | Add integration test that consumes bucket and asserts 429 + Retry-After |
| Shopper checkout idempotency and 12-hour timestamp | src/test/java/com/meridianmart/integration/OrderControllerTest.java:64; src/test/java/com/meridianmart/unit/OrderServiceTest.java:123 | Same receipt for duplicate key; timestamp AM/PM assertion | basically covered | No conflict test for key reuse across different shopper | Add integration test for key reuse by different user returns 409 |
| Staff payment authz + idempotency + cross-store | src/test/java/com/meridianmart/integration/PaymentControllerTest.java:70, :80, :141, :162 | 403/401/404/409 assertions | sufficient | No concurrency stress path | Add lock contention integration test |
| Notification daily cap | src/test/java/com/meridianmart/integration/NotificationControllerTest.java:42; src/test/java/com/meridianmart/unit/NotificationServiceTest.java:56 | 429 on sixth notification | basically covered | No mark-as-read unauthorized/non-owner path | Add mark-read negative authz/object-level tests |
| Recommendation cold start/cache/min shared | src/test/java/com/meridianmart/unit/RecommendationServiceTest.java:67, :115, :135 | Cold-start, cached TTL, shared-threshold behavior | basically covered | Integration tests do not verify feature-flag disabled path | Add integration test with RECOMMENDATIONS_ENABLED=false |
| Admin config masking and RBAC | src/test/java/com/meridianmart/integration/AdminControllerTest.java:85, :96 | Masked sensitive value; shopper forbidden | sufficient | No regression test for encrypted storage at repo level | Add repository-level assertion for encrypted stored value |
| Store isolation (staff/payment/refund) | src/test/java/com/meridianmart/integration/StaffControllerTest.java:107, :115, :127; src/test/java/com/meridianmart/integration/PaymentControllerTest.java:141 | 404 for cross-store access | sufficient for staff/payment flows | Shopper cross-store/order history negatives limited | Add shopper isolation tests on order/notification flows |
| Rating constraints (1..5 + purchased-only) | src/test/java/com/meridianmart/integration/RatingControllerTest.java:70, :79, :88 | 400 on out-of-range, 403 if not purchased | sufficient | No assertion that rating creates behavior event | Add backend event persistence assertion |

### 8.3 Security Coverage Audit
- Authentication: basically covered
  - Evidence: src/test/java/com/meridianmart/integration/AuthControllerTest.java:16, :52, :78
- Route authorization: basically covered
  - Evidence: src/test/java/com/meridianmart/integration/PaymentControllerTest.java:70, src/test/java/com/meridianmart/integration/StaffControllerTest.java:70, src/test/java/com/meridianmart/integration/AdminControllerTest.java:52
- Object-level authorization: insufficient
  - Evidence: good coverage exists in some domains (Staff/Payment cross-store), but sparse for notifications/order-history ownership negatives.
  - Evidence refs: src/test/java/com/meridianmart/integration/StaffControllerTest.java:107; src/test/java/com/meridianmart/integration/NotificationControllerTest.java:30
- Tenant/data isolation: basically covered
  - Evidence: src/test/java/com/meridianmart/integration/PaymentControllerTest.java:141, src/test/java/com/meridianmart/integration/StaffControllerTest.java:107
- Admin/internal protection: covered
  - Evidence: src/test/java/com/meridianmart/integration/AdminControllerTest.java:52, :96

### 8.4 Final Coverage Judgment
- Final coverage judgment: Partial Pass
- Boundary:
  - Major risks covered: core authn/authz, payment role checks, idempotency conflict paths, recommendation cold-start/cache threshold, notification cap.
  - Uncovered risks that can allow severe defects while tests still pass: secret-key signing semantics, some object-level authorization negatives (notably notifications/order-history), and explicit rate-limit threshold exhaustion.

## 9. Final Notes
- This report is static-only and evidence-based; no runtime success is claimed.
- Where static proof is insufficient, the report marks manual verification boundaries.
- Root-cause issues are merged to avoid repetitive symptom inflation.
