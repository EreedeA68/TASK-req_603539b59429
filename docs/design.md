# MeridianMart Offline Commerce — Architecture & Design

## System Architecture Overview

MeridianMart runs entirely on an internal store network with no external internet dependencies. The frontend is built with Thymeleaf, which renders server-side HTML pages from Spring Boot controllers. Spring Boot exposes REST-style endpoints that Thymeleaf pages consume via standard HTTP calls within the same JVM process, keeping the stack fully decoupled while remaining offline-capable. MySQL 8 stores all persistent state, and the entire system is packaged with Docker Compose for single-command deployment.

```
+-------------------------------------------------------------+
|                        Docker Compose                       |
|                                                             |
|  +--------------------+       +-------------------------+  |
|  |   Spring Boot 3    |<----->|      MySQL 8            |  |
|  |   (Java 17)        |       |                         |  |
|  |                    |       |  - users / sessions     |  |
|  |  Thymeleaf Views   |       |  - products / catalog   |  |
|  |  REST Controllers  |       |  - cart / orders        |  |
|  |  Service Layer     |       |  - payments             |  |
|  |  Recommendation    |       |  - notifications        |  |
|  |  Engine            |       |  - audit_logs           |  |
|  |  Scheduled Jobs    |       |  - behavior_events      |  |
|  +--------------------+       +-------------------------+  |
|           |                                                 |
|           +-----> /data/backups  (local disk)              |
+-------------------------------------------------------------+
        ^
        | HTTP (internal store network)
        |
  Store terminals / POS registers / Admin workstations
```

**Request flow:** A browser on the store network hits Spring Boot. The controller authenticates the session, validates the request, calls the relevant service, and returns a Thymeleaf-rendered HTML page. For AJAX interactions (cart updates, notification polling), the same controllers return JSON responses consumed by lightweight JavaScript on the page.

---

## Role Definitions

| Role | Description |
|------|-------------|
| **Regular Shopper** | Authenticated store customer. Can browse catalog, manage cart and favorites, complete checkout at a staffed register, rate purchases, and view personalized recommendations and notifications. |
| **Store Staff** | Authenticated store employee. Can perform all Shopper actions plus look up transactions by receipt number, initiate refunds, and mark orders as ready for pickup. |
| **Administrator** | Full system access. Manages feature flags, operational thresholds, and can review compliance reports. Inherits all Staff capabilities. |
| **Read-Only** | Restricted observer role. Can browse the catalog and view reports but cannot modify any data. Used for auditors and oversight personnel. |

---

## Core Modules

### Auth Module
Handles local username/password authentication. Sessions are stored server-side in MySQL. Passwords are hashed with bcrypt using salted hashing. Brute-force protection locks accounts for 15 minutes after 5 failed login attempts. All login failures are written to the immutable audit log. Session tokens are issued as JWTs signed with a server-side secret.

### Catalog Module
Manages the product catalog including product records, categories, descriptions, prices, and on-hand stock quantities. Exposes browsing and search endpoints. Displays stock warnings on product pages and in cart when on-hand quantity falls below 2 units.

### Cart Module
Maintains per-shopper shopping carts persisted in MySQL. Supports add, update quantity, and remove operations. Provides clear UI feedback ("added to cart"). Reflects current stock status on cart items.

### Orders Module
Manages the full order lifecycle from checkout initiation through POS confirmation. Generates a unique receipt number and records a transaction timestamp in 12-hour format. Presents a purchase confirmation screen with printable receipt details. Integrates with the payment module for offline settlement.

### Recommendations Module
Collaborative filtering engine that records shopper behavior events (views, favorites, add-to-cart, purchases, ratings) to build a user–item interaction matrix. Generates Top-10 recommendations per shopper using cosine or Pearson correlation similarity. See full design in the Recommendation Engine Design section.

### Payments Module
Handles offline WeChat Pay-presented transactions recorded from POS confirmations without calling external gateways. Manages idempotency, refunds, deposits/pre-authorizations, reconciliation, and failed-payment compensation. See full design in the Payment Design section.

### Notifications Module
Delivers in-app notifications for order status updates and pickup readiness. Enforces a cap of 5 notifications per day per shopper to prevent spam. Notification state (read/unread) is tracked per user.

### Audit Module
Maintains an append-only audit log table for critical actions. Logged events include: login failures, successful logins, logouts, refund initiations, configuration changes (feature flags, thresholds), privilege escalations, sensitive data reads, and backup/restore operations. Each entry captures actor ID, actor role snapshot, timestamp, action type, target entity, and request correlation ID. Audit records are immutable — no UPDATE or DELETE is permitted on this table.

### Backup Module
Runs daily full backups to local disk. Retains backups for 30 days. Restore operations are logged with a trace record capturing who initiated the restore, from which backup file, and the outcome. Anonymization jobs run as part of scheduled maintenance.

---

## Database Design Summary

### users
Stores shopper, staff, and admin accounts. Fields: `id`, `username`, `password_hash` (bcrypt), `role` (SHOPPER / STAFF / ADMIN / READONLY), `status` (ACTIVE / LOCKED), `locked_until`, `failed_login_count`, `created_at`, `last_login_at`, `anonymized` (boolean), `anonymized_at`.

### sessions
Server-side session store. Fields: `id`, `user_id` (FK → users), `token_hash`, `created_at`, `expires_at`, `revoked`.

### login_attempts
Rolling window tracking for brute-force protection. Fields: `id`, `user_id` (FK → users), `attempted_at`, `success` (boolean), `ip_address`.

### products
Product catalog. Fields: `id`, `name`, `description`, `category_id` (FK → categories), `price`, `on_hand_qty`, `active` (boolean), `created_at`, `updated_at`.

### categories
Product categories. Fields: `id`, `name`, `parent_id` (self-referencing FK, nullable).

### cart_items
Shopping cart. Fields: `id`, `user_id` (FK → users), `product_id` (FK → products), `quantity`, `added_at`, `updated_at`. Unique constraint on `(user_id, product_id)`.

### favorites
Shopper favorites list. Fields: `id`, `user_id` (FK → users), `product_id` (FK → products), `added_at`. Unique constraint on `(user_id, product_id)`.

### orders
Order header. Fields: `id`, `receipt_number` (unique, generated), `user_id` (FK → users), `status` (PENDING / CONFIRMED / READY_FOR_PICKUP / COMPLETED / REFUNDED / CANCELLED), `total_amount`, `created_at`, `confirmed_at`, `idempotency_key` (unique).

### order_items
Order line items. Fields: `id`, `order_id` (FK → orders), `product_id` (FK → products), `quantity`, `unit_price`.

### payments
Payment records. Fields: `id`, `order_id` (FK → orders), `idempotency_key` (unique), `method` (WECHAT_PAY_OFFLINE), `status` (PENDING / CONFIRMED / FAILED / REFUNDED), `amount`, `pos_confirmation_ref`, `created_at`, `settled_at`, `lock_token`.

### refunds
Refund records. Fields: `id`, `payment_id` (FK → payments), `order_id` (FK → orders), `initiated_by` (FK → users), `amount`, `reason`, `status` (PENDING / COMPLETED / FAILED), `idempotency_key` (unique), `created_at`, `completed_at`.

### ratings
Product ratings from shoppers. Fields: `id`, `user_id` (FK → users), `product_id` (FK → products), `order_id` (FK → orders), `score` (1–5), `created_at`. Unique constraint on `(user_id, product_id, order_id)`.

### behavior_events
Tracks shopper interactions for the recommendation engine. Fields: `id`, `user_id` (FK → users), `product_id` (FK → products), `event_type` (VIEW / FAVORITE / ADD_TO_CART / PURCHASE / RATING), `event_value` (numeric, used for rating score; null for other types), `occurred_at`.

### recommendations
Cached recommendation results. Fields: `id`, `user_id` (FK → users), `product_id` (FK → products), `score` (float), `rank` (1–10), `generated_at`, `expires_at`.

### notifications
In-app notification records. Fields: `id`, `user_id` (FK → users), `type` (ORDER_STATUS / PICKUP_READY), `message`, `order_id` (FK → orders, nullable), `read` (boolean), `created_at`.

### notification_daily_counts
Enforces per-user daily notification cap. Fields: `id`, `user_id` (FK → users), `date` (DATE), `count`. Unique constraint on `(user_id, date)`.

### feature_flags
Admin-managed feature toggles. Fields: `id`, `name` (unique), `enabled` (boolean), `description`, `updated_by` (FK → users), `updated_at`.

### operational_thresholds
Admin-managed numeric thresholds (e.g., stock warning level, rate limit overrides). Fields: `id`, `key` (unique), `value`, `description`, `updated_by` (FK → users), `updated_at`.

### audit_logs
Immutable audit trail. Fields: `id`, `actor_id` (FK → users, nullable for system), `actor_role`, `action`, `target_type`, `target_id`, `detail_json`, `ip_address`, `correlation_id`, `occurred_at`.

### encrypted_credentials
Stores AES-256 encrypted sensitive values (payment tokens, etc.). Fields: `id`, `owner_id`, `owner_type`, `field_name`, `encrypted_value`, `key_version`, `created_at`.

### backup_traces
Restore traceability. Fields: `id`, `backup_file`, `initiated_by` (FK → users), `initiated_at`, `completed_at`, `status` (SUCCESS / FAILED), `detail`.

### change_history
Key-field change history for sensitive fields. Fields: `id`, `table_name`, `record_id`, `field_name`, `old_value_masked`, `new_value_masked`, `changed_by` (FK → users), `changed_at`.

**Relationships summary:**
- `users` ← `sessions`, `login_attempts`, `cart_items`, `favorites`, `orders`, `ratings`, `behavior_events`, `recommendations`, `notifications`, `notification_daily_counts`, `refunds` (as initiator), `audit_logs` (as actor)
- `products` ← `cart_items`, `favorites`, `order_items`, `ratings`, `behavior_events`, `recommendations`
- `orders` ← `order_items`, `payments`, `refunds`, `notifications`
- `payments` ← `refunds`
- `categories` ← `products`

---

## Security Design

### Password Hashing
Passwords are hashed using bcrypt with a per-user salt. Raw passwords are never stored or logged. The `password_hash` column stores only the bcrypt output.

### Credential & Token Encryption
Sensitive data at rest (payment tokens, POS confirmation references) is encrypted using AES-256. Encryption keys are versioned to support rotation. The active key version is stored alongside the ciphertext in `encrypted_credentials`.

### JWT Session Tokens
Upon successful login, a JWT is issued containing the user ID, role, and expiry. The token is signed with an HMAC-SHA256 server secret. All API endpoints validate the JWT on every request before processing.

### Rate Limiting
Default rate limit is 60 requests per minute per user. Rate limit counters are maintained in MySQL with a sliding window. Requests exceeding the limit receive HTTP 429.

### Replay Attack Prevention
Every API request must include a `X-Timestamp` header (Unix epoch) and a `X-Nonce` header (unique random value). The server rejects requests where the timestamp is outside a ±5-minute window. Used nonces are stored in MySQL for 10 minutes with a unique constraint, so replaying a captured request within the window is rejected with HTTP 400.

### Brute-Force Lockout
After 5 consecutive failed login attempts, the account `status` is set to LOCKED and `locked_until` is set to 15 minutes from the last failure. All subsequent login attempts during the lockout period return HTTP 403. Lockout events are written to `audit_logs`. Administrators can manually unlock accounts.

### Sensitive Data Masking
Payment token values, government IDs, and other sensitive fields are masked when displayed on-screen and in exported reports. Masking applies the format `****XXXX` showing only the last 4 characters. Exports strip or hash sensitive fields as required by the compliance report configuration.

### Immutable Audit Logs
The `audit_logs` table has no UPDATE or DELETE privileges granted at the database level. The application service layer only ever inserts into this table. Critical actions that always trigger audit entries: login failures, successful logins after brute-force warning, logouts, all refund initiations, all feature flag changes, all threshold changes, configuration updates, sensitive data reads by Staff/Admin, backup initiation and restore events.

### Key-Field Change History
Changes to sensitive fields (product prices, user roles, payment amounts) are recorded in `change_history` with masked before/after values, the actor, and timestamp. This provides a tamper-evident trail for compliance reviews.

---

## Recommendation Engine Design

### Behavioral Data Collection
The engine records five event types per shopper–product pair in `behavior_events`:
- **VIEW** — shopper viewed product detail page
- **FAVORITE** — shopper added product to favorites
- **ADD_TO_CART** — shopper added product to cart
- **PURCHASE** — shopper completed purchase of product
- **RATING** — shopper submitted a 1–5 rating

### User–Item Interaction Matrix
Events are aggregated into a weighted interaction score per `(user_id, product_id)` pair. Weights: VIEW=1, FAVORITE=2, ADD_TO_CART=3, PURCHASE=5, RATING=event_value. This matrix forms the input for collaborative filtering.

### Similarity Computation
Pairwise user similarity is computed using either cosine similarity or Pearson correlation on the interaction vectors. The method is configurable via an operational threshold. **Sparse-data safeguard:** A similarity score is only used if the two users share at least 3 product interactions (i.e., both have a non-zero interaction score for at least 3 of the same products). Scores based on fewer than 3 shared interactions are discarded to prevent noise-driven recommendations.

### Top-10 Recommendations
For each shopper, the engine identifies the K most similar users (above the sparse-data threshold), aggregates their interaction-weighted product scores for products the target shopper has not yet purchased, and selects the Top-10 by predicted score. Results are stored in the `recommendations` table.

### Cold Start Fallback
When a shopper has insufficient interaction history (fewer than 5 total events or no similar users found above the threshold), the engine falls back to:
1. **Category popularity** — most-purchased products within the same category preferences inferred from any existing events, computed over the last 30 days.
2. **New arrivals** — products added to the catalog within the last 14 days, ordered by `created_at` descending.

Fallback results are marked as cold-start in the `recommendations` table and are replaced as soon as real collaborative filtering becomes viable.

### Caching
Recommendation results are cached in the `recommendations` table with an `expires_at` timestamp of 60 minutes from generation. API requests for recommendations check whether valid (non-expired) rows exist for the user before triggering recomputation. Expired rows are replaced on the next scheduled refresh or on-demand recomputation.

### Nightly Refresh
A Spring Boot `@Scheduled` job runs every night at 2:00 AM. It iterates over all active shoppers, recomputes their recommendations using the latest behavior data, updates the `recommendations` table, and sets `expires_at` to 60 minutes from generation time. If the job fails for any shopper, the failure is logged to `audit_logs` and that shopper retains their previously cached recommendations (which may be stale) until the next successful run or an on-demand request triggers recomputation.

---

## Payment Design

### Offline POS Model
MeridianMart does not call any external payment gateway. Shoppers present WeChat Pay QR codes at a staffed register. The POS device confirms the transaction locally and the cashier enters the POS confirmation reference into the system. The backend records the payment in the `payments` table with `method = WECHAT_PAY_OFFLINE` and `pos_confirmation_ref` from the POS device.

### Idempotency Keys
Every order creation and payment recording request must include a client-generated `idempotency_key` (UUID). The server stores this key in the `orders.idempotency_key` and `payments.idempotency_key` columns with a unique constraint. If a request arrives with a key that already exists, the server returns the existing resource (HTTP 200) rather than creating a duplicate. This prevents double order creation and double payment recording under network retries or concurrent submissions.

### Distributed Locks
To prevent concurrent double-posting of payments for the same order, the backend acquires a MySQL advisory lock using `GET_LOCK('payment:{order_id}', timeout)` before processing a payment write. The lock token is stored in `payments.lock_token`. If the lock cannot be acquired within the timeout, the request returns HTTP 409. Locks are released immediately after the write completes. This approach requires no external dependencies such as Redis.

### Refunds
Refunds are initiated by Store Staff via the refund endpoint. Each refund is recorded in the `refunds` table with its own `idempotency_key`. Refund processing updates `payments.status` to REFUNDED and `orders.status` to REFUNDED. Refund events are written to `audit_logs`.

### Deposits and Pre-Authorizations
Optional deposit or pre-authorization records can be created before full payment confirmation. These are recorded in `payments` with `status = PENDING` until the POS confirms settlement.

### Reconciliation
Administrators can pull a compliance/reconciliation report via `GET /api/compliance-reports` that summarizes all payments, refunds, and net settlement for a given date range. The report uses only data from the `payments`, `refunds`, and `orders` tables — no external reconciliation service is required.

### Failed Payment Compensation
If a payment record is inserted but the subsequent order status update fails (e.g., due to a database error), the system detects the inconsistency on the next reconciliation run. The `idempotency_key` ensures the payment is not re-inserted; the compensation job updates the order status to match the confirmed payment record. All compensation actions are audit-logged.

---

## Data Retention

### Shopper Profile Anonymization
Shopper profiles that have been inactive for 24 months are automatically anonymized by a scheduled job. "Inactive" means no login, no order, no cart activity, and no open dispute in the last 24 months. Anonymization replaces `username`, `password_hash`, and any profile fields with a deterministic anonymization marker (e.g., `anon_{id}`), sets `users.anonymized = true`, and records `users.anonymized_at`. Shopper behavior events are dissociated from the user ID (user_id set to NULL) but retained in aggregate for the recommendation engine.

**Open disputes** are defined as: any order in status PENDING, CONFIRMED, READY_FOR_PICKUP, or any refund in status PENDING. A profile tied to an open dispute is excluded from anonymization regardless of inactivity duration.

### Backup Retention
Daily full backups are retained on local disk for 30 days. Backups older than 30 days are automatically purged by the backup maintenance job. Each backup file is named with a UTC timestamp for traceability.

### Restore Traceability
Every restore operation creates a record in `backup_traces` capturing the backup file used, the administrator who initiated the restore, the start and completion timestamps, and the outcome. This provides a complete chain of custody for data restoration.
