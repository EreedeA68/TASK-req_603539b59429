# Follow-Up Review of Previously Reported Issues

Date: 2026-04-19

## Scope and Boundary

- Reviewed the previously reported issues from .tmp/meridianmart-static-audit-2026-04-19.md.
- Static analysis only.
- Did not start the project, run tests, run Docker, or perform browser/manual checks.
- Conclusions below are limited to what is provable from the current repository contents.

## Summary

- Fixed: 4
- Partially Fixed: 0
- Not Fixed: 0

## Issue-by-Issue Verification

### 1. Request-signing secret configured but not used for authenticated signatures
- Status: Fixed
- Rationale: Signature verification for authenticated API requests now uses the server-held request-signing secret, and login now returns a dedicated signing key for frontend signing. This replaces the earlier token-derived signing behavior.
- Evidence:
  - meridianmart/src/main/java/com/meridianmart/security/RequestSigningFilter.java:46
  - meridianmart/src/main/java/com/meridianmart/security/RequestSigningFilter.java:117
  - meridianmart/src/main/java/com/meridianmart/security/RequestSigningFilter.java:118
  - meridianmart/src/main/java/com/meridianmart/service/AuthService.java:39
  - meridianmart/src/main/java/com/meridianmart/service/AuthService.java:99
  - meridianmart/src/main/resources/static/js/app.js:7
  - meridianmart/src/main/resources/static/js/app.js:17
  - meridianmart/src/main/resources/static/js/app.js:151

### 2. Behavior-event recording for recommendation signals was client-coupled and not backend-enforced
- Status: Fixed
- Rationale: Core interaction flows now persist behavior events in backend services (add-to-cart, favorite, rating, and view endpoint path), eliminating the prior dependency on client-side telemetry for essential recommendation data capture.
- Evidence:
  - meridianmart/src/main/java/com/meridianmart/service/CartService.java:57
  - meridianmart/src/main/java/com/meridianmart/service/CartService.java:58
  - meridianmart/src/main/java/com/meridianmart/service/FavoriteService.java:44
  - meridianmart/src/main/java/com/meridianmart/service/FavoriteService.java:45
  - meridianmart/src/main/java/com/meridianmart/service/RatingService.java:60
  - meridianmart/src/main/java/com/meridianmart/service/RatingService.java:61
  - meridianmart/src/main/java/com/meridianmart/service/ProductService.java:87
  - meridianmart/src/main/java/com/meridianmart/service/ProductService.java:89
  - meridianmart/src/main/java/com/meridianmart/service/ProductService.java:90

### 3. High-risk authorization and behavior coverage gaps in integration tests
- Status: Fixed
- Rationale: Prior missing checks are now present: notification mark-read ownership and unauthenticated negatives, shopper order-history isolation, and recommendation-disabled feature-flag behavior.
- Evidence:
  - meridianmart/src/test/java/com/meridianmart/integration/NotificationControllerTest.java:53
  - meridianmart/src/test/java/com/meridianmart/integration/NotificationControllerTest.java:68
  - meridianmart/src/test/java/com/meridianmart/integration/OrderControllerTest.java:110
  - meridianmart/src/test/java/com/meridianmart/integration/RecommendationControllerTest.java:54

### 4. README path was overly Docker-centric for local reproducibility
- Status: Fixed
- Rationale: Documentation now includes explicit non-Docker local run and test instructions using Maven and H2.
- Evidence:
  - meridianmart/README.md:86
  - meridianmart/README.md:88
  - meridianmart/README.md:97
  - meridianmart/README.md:118
  - meridianmart/README.md:120

## Final Follow-Up Verdict

All four issues reported in the previous static audit are resolved in the current repository state based on static, file-level verification.
