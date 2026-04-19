# Test Coverage Audit

## Scope and Method
- Mode: static inspection only (no test execution performed).
- Inspected areas: controllers, tests under Java and JS test trees, README, run_tests.sh.
- Project type declaration at top README: inferred fullstack from "A full-stack offline commerce platform" in meridianmart/README.md:3.

## Backend Endpoint Inventory

### API Endpoints
1. GET /api/feature-flags
2. PUT /api/feature-flags/{id}
3. GET /api/compliance-reports
4. GET /api/config
5. PUT /api/config/{key}
6. POST /api/auth/login
7. POST /api/auth/logout
8. GET /api/auth/me
9. POST /api/behavior
10. POST /api/cart
11. GET /api/cart
12. DELETE /api/cart/{itemId}
13. POST /api/favorites
14. GET /api/favorites
15. DELETE /api/favorites/{id}
16. GET /api/notifications
17. PUT /api/notifications/{id}/read
18. POST /api/orders
19. GET /api/orders
20. PUT /api/orders/{id}/ready-for-pickup
21. POST /api/payments/deposit
22. POST /api/payments/capture
23. POST /api/payments/pre-authorize
24. GET /api/products
25. GET /api/products/{id}
26. POST /api/ratings
27. GET /api/recommendations
28. GET /api/transactions/{receiptNumber}
29. POST /api/orders/{id}/pos-confirm
30. POST /api/refunds

### Page Routes
31. GET /
32. GET /login
33. GET /home
34. GET /products/{id}
35. GET /cart
36. GET /checkout
37. GET /confirmation
38. GET /orders
39. GET /notifications
40. GET /favorites
41. GET /staff/dashboard
42. GET /admin/dashboard

## API Test Mapping Table

| Endpoint | Covered | Test Type | Test Files | Evidence |
|---|---|---|---|---|
| GET /api/feature-flags | yes | true no-mock HTTP | integration/AdminControllerTest.java | getFeatureFlagsAsAdminReturns200WithFlagNameIsEnabled |
| PUT /api/feature-flags/{id} | yes | true no-mock HTTP | integration/AdminControllerTest.java | updateFeatureFlagAsAdminReturns200WithUpdatedState |
| GET /api/compliance-reports | yes | true no-mock HTTP | integration/AdminControllerTest.java | getComplianceReportsAsAdminReturns200WithReportData |
| GET /api/config | yes | true no-mock HTTP | integration/AdminControllerTest.java | getConfigsAsAdminReturns200WithConfigList |
| PUT /api/config/{key} | yes | true no-mock HTTP | integration/AdminControllerTest.java | upsertConfigAsAdminReturns200WithUpdatedValue |
| POST /api/auth/login | yes | true no-mock HTTP | integration/AuthControllerTest.java | loginWithValidUsernameReturns200WithTokenRoleUserId |
| POST /api/auth/logout | yes | true no-mock HTTP | integration/AuthControllerTest.java | logoutWithValidTokenReturns200 |
| GET /api/auth/me | yes | true no-mock HTTP | integration/AuthControllerTest.java, integration/SecurityTest.java | getMeWithValidJwtReturnsProfileWithoutPassword |
| POST /api/behavior | yes | true no-mock HTTP | integration/BehaviorControllerTest.java | recordValidBehaviorEventAsShopperReturns200 |
| POST /api/cart | yes | true no-mock HTTP | integration/CartControllerTest.java, integration/OrderControllerTest.java | addToCartAsShopperReturns201WithCartItem |
| GET /api/cart | yes | true no-mock HTTP | integration/CartControllerTest.java | getCartReturns200WithItemsAndTotalPrice |
| DELETE /api/cart/{itemId} | yes | true no-mock HTTP | integration/CartControllerTest.java | deleteCartItemRemovesItFromCart |
| POST /api/favorites | yes | true no-mock HTTP | integration/FavoriteControllerTest.java | addFavoriteAsShopperReturns201 |
| GET /api/favorites | yes | true no-mock HTTP | integration/FavoriteControllerTest.java | getFavoritesReturnsShopperFavorites |
| DELETE /api/favorites/{id} | yes | true no-mock HTTP | integration/FavoriteControllerTest.java | removeFavoriteByOwnerSucceeds |
| GET /api/notifications | yes | true no-mock HTTP | integration/NotificationControllerTest.java | getNotificationsReturns200WithArray |
| PUT /api/notifications/{id}/read | yes | true no-mock HTTP | integration/NotificationControllerTest.java | markReadAsNonOwnerReturns404 |
| POST /api/orders | yes | true no-mock HTTP | integration/OrderControllerTest.java | checkoutReturns201WithReceiptNumberTimestampStatus |
| GET /api/orders | yes | true no-mock HTTP | integration/OrderControllerTest.java | getOrderHistoryReturns200WithReceiptAndStatus |
| PUT /api/orders/{id}/ready-for-pickup | yes | true no-mock HTTP | integration/StaffControllerTest.java | markReadyForPickupAsStaffReturns200WithCorrectStatus |
| POST /api/payments/deposit | yes | true no-mock HTTP | integration/PaymentControllerTest.java | depositByStaffReturns201WithTransactionDetails |
| POST /api/payments/capture | yes | true no-mock HTTP | integration/PaymentControllerTest.java | captureByStaffReturns200WithTransactionDetails |
| POST /api/payments/pre-authorize | yes | true no-mock HTTP | integration/PaymentControllerTest.java | preAuthorizeByStaffReturns201WithPreAuthorizedStatus |
| GET /api/products | yes | true no-mock HTTP | integration/ProductControllerTest.java, integration/SecurityTest.java | getCatalogReturns200WithItemsPageSizeTotal |
| GET /api/products/{id} | yes | true no-mock HTTP | integration/ProductControllerTest.java | getProductReturns200WithFullFields |
| POST /api/ratings | yes | true no-mock HTTP | integration/RatingControllerTest.java | rateProductScore1to5Returns201 |
| GET /api/recommendations | yes | true no-mock HTTP | integration/RecommendationControllerTest.java | getRecommendationsReturns200WithUpTo10Products |
| GET /api/transactions/{receiptNumber} | yes | true no-mock HTTP | integration/StaffControllerTest.java | getTransactionAsStaffReturns200WithFullPayload |
| POST /api/orders/{id}/pos-confirm | yes | true no-mock HTTP | integration/StaffControllerTest.java | posConfirmByStaffReturns200WithCompletedStatus |
| POST /api/refunds | yes | true no-mock HTTP | integration/StaffControllerTest.java | refundAsStaffReturns200WithRefundIdAndStatus |

## API Test Classification

### 1) True No-Mock HTTP
- All API integration tests under meridianmart/src/test/java/com/meridianmart/integration/*.java using @SpringBootTest + @AutoConfigureMockMvc via meridianmart/src/test/java/com/meridianmart/integration/BaseIntegrationTest.java.

### 2) HTTP with Mocking
- None detected for API integration tests.

### 3) Non-HTTP (unit/integration without HTTP)
- Unit tests under meridianmart/src/test/java/com/meridianmart/unit/*.java using Mockito mocks.
- JS unit tests under meridianmart/src/test/js/app.test.js using Jest/jsdom.

## Mock Detection
- Detected in backend unit tests only:
  - @Mock and when(...) patterns across unit tests (AuthServiceTest, PaymentServiceTest, OrderServiceTest, CartServiceTest, ProductServiceTest, NotificationServiceTest, RecommendationServiceTest, AuditServiceTest).
- Not detected in API integration tests:
  - no @MockBean and no @WebMvcTest usage in integration package.

## Coverage Summary

### API-only coverage
- Total API endpoints: 30.
- API endpoints with HTTP tests: 30.
- API endpoints with true no-mock HTTP tests: 30.
- HTTP coverage: 100.0%.
- True API coverage: 100.0%.

### Overall route coverage (API + page routes)
- Total routes: 42.
- Routes with HTTP/browser tests: 42.
- Uncovered routes: none found.

## Unit Test Summary

### Backend Unit Tests
- Present: yes (8 files in unit package).
- Modules covered:
  - services: strong coverage.
  - controllers: no direct unit tests (covered via integration tests).
  - repositories: no direct unit tests.
  - auth/guards/middleware internals: limited direct unit coverage.
- Important backend modules not unit-tested:
  - security filter internals in com/meridianmart/security/*
  - scheduler behavior in com/meridianmart/scheduler/*

### Frontend Unit Tests (STRICT REQUIREMENT)
- Frontend test files: meridianmart/src/test/js/app.test.js.
- Framework/tooling detected: Jest + jsdom (meridianmart/package.json and file header @jest-environment jsdom).
- Evidence tests import actual frontend module:
  - require('../../main/resources/static/js/app.js') in meridianmart/src/test/js/app.test.js.
- Components/modules covered:
  - API auth state helpers (saveAuth/logout/computeSignature)
  - utility formatters (formatPrice/getStatusClass)
- Important frontend modules not unit-tested:
  - most page-flow logic in meridianmart/src/main/resources/static/js/app.js remains ununit-tested.

Mandatory verdict:
- Frontend unit tests: PRESENT.

Strict failure rule result:
- No critical gap from frontend-unit absence (gap resolved).

### Cross-Layer Observation
- Backend API integration and route coverage is now complete.
- Frontend coverage includes both browser tests (Selenium/e2e) and at least one true frontend unit test suite.
- Balance improved, though frontend unit depth remains shallow relative to backend.

## API Observability Check
- Strong in most API tests: explicit method/path, request payload, status, and response assertions (jsonPath).
- Weak cases remain where assertions are status-only in some authorization-path tests.

## Tests Check
- Success paths: covered.
- Failure paths: covered (401/403/404/409/423/429).
- Edge cases: covered (idempotency collisions, cross-store isolation, nonce replay, lock contention patterns).
- Validation/auth/permissions: covered.
- Integration boundaries: strong.
- Assertion depth: mostly meaningful, with minor status-only cases.

run_tests.sh check:
- Docker-based test execution: yes.
- Local dependency requirement in script: no.

## End-to-End Expectations
- fullstack FE↔BE e2e present via Selenium e2e journeys.
- Additional frontend unit suite now present (Jest), improving test pyramid.

## Test Coverage Score (0-100)
- Score: 91/100.

## Score Rationale
- + 100% API endpoint coverage with true no-mock HTTP style.
- + Full route coverage including server pages.
- + Strong negative and security-path testing.
- + Frontend unit tests now present with direct module import evidence.
- - Frontend unit depth is still narrow (single suite, utility-heavy).
- - A subset of tests still uses shallow status-only assertions.

## Key Gaps
1. Frontend unit tests focus mainly on utility/auth-helper behaviors; broader UI-state logic in app.js is still lightly unit-tested.
2. Some API tests assert status only without richer body contract checks.
3. Security/scheduler internals lack dedicated unit tests.

## Confidence and Assumptions
- Confidence: high.
- Assumptions:
  - conclusions are based on repository-visible tests only.
  - no hidden/generated tests outside inspected tree.

---

# README Audit

## README Location Check
- File exists at meridianmart/README.md.

## Hard Gate Evaluation

### Formatting
- PASS.

### Startup Instructions (Backend/Fullstack requires docker-compose up)
- PASS.
- Evidence: docker-compose up --build -d in meridianmart/README.md:67.

### Access Method (URL + port)
- PASS.
- Evidence: http://localhost:8080 in meridianmart/README.md:70.

### Verification Method
- PASS.
- Evidence: explicit smoke verification steps with curl and expected outcomes in meridianmart/README.md:85.

### Environment Rules (Docker-contained)
- PASS.
- No local package/runtime install workflows documented.

### Demo Credentials (auth exists)
- PASS.
- Includes roles with email and password in meridianmart/README.md:122.

## Engineering Quality
- Tech stack clarity: strong.
- Architecture explanation: strong.
- Testing instructions: strong.
- Security/roles/workflows: strong.
- Presentation quality: high.

## High Priority Issues
- None.

## Medium Priority Issues
1. Could add one explicit end-to-end business verification flow (login -> add cart -> checkout -> staff POS confirm) alongside smoke checks.

## Low Priority Issues
1. Could explicitly label project type line as "Project type: fullstack" for strict parsers.

## Hard Gate Failures
- None.

## README Verdict
- PASS.

---

## Final Verdicts
- Test Coverage Audit Verdict: PASS (with minor quality gaps).
- README Audit Verdict: PASS.
