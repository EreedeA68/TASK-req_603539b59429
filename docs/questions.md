# MeridianMart Offline Commerce — Design Questions and Answers

## 1. How are cold start recommendations handled?

**Question:** A new shopper has no interaction history. What does the recommendation engine return so the home page and product pages are not empty?

**Answer:** When a shopper has fewer than 5 total behavior events or no similar users can be found above the sparse-data threshold (minimum 3 shared interactions), the engine falls back to two ordered strategies. First, it uses **category popularity**: the most-purchased products within the category preferences inferred from any existing events, computed over the last 30 days. If no category preference can be inferred (a brand new shopper with zero events), it falls back to **new arrivals**: products added to the catalog within the last 14 days, ordered by creation date descending. Cold-start results are flagged with `coldStart: true` in the API response and are replaced automatically as soon as sufficient interaction data accumulates to enable real collaborative filtering.

---

## 2. What happens during concurrent payment attempts?

**Question:** If two requests try to confirm payment for the same order at the same time (e.g., a cashier double-submits), how does the system prevent double posting?

**Answer:** Two mechanisms work in combination. First, the **idempotency key**: every order creation and payment recording request must include a client-generated UUID `idempotencyKey`. The server stores this key in the `payments.idempotency_key` column with a unique database constraint. A duplicate submission with the same key returns the existing payment record (HTTP 200) rather than creating a new one. Second, a **distributed lock** is acquired using MySQL's `GET_LOCK('payment:{order_id}', timeout)` advisory lock before any payment write is performed. If a concurrent request holds the lock, the second request receives HTTP 409. The lock is released immediately after the write completes, requiring no external dependencies such as Redis or ZooKeeper.

---

## 3. How is the 5 notifications/day cap enforced?

**Question:** Shoppers receive in-app notifications for order status changes and pickup readiness. How does the system prevent sending more than 5 per day?

**Answer:** A dedicated `notification_daily_counts` table maintains a row per `(user_id, date)` with a counter column. Before any notification is inserted into the `notifications` table, the system reads the counter for that shopper on the current calendar date. If the counter has already reached 5, the notification is silently suppressed and the calling endpoint returns `notificationSent: false` in its response body. If the counter is below 5, the notification is inserted and the counter is incremented atomically (using a single `INSERT ... ON DUPLICATE KEY UPDATE count = count + 1` statement) to prevent race conditions. The counter resets naturally because a new date produces a new row.

---

## 4. When is the stock warning shown and what triggers it?

**Question:** The project requires a stock warning in the UI. When exactly is it displayed and what is the threshold?

**Answer:** A stock warning is displayed whenever a product's `on_hand_qty` field falls **below 2 units** (i.e., when `on_hand_qty < 2`). The warning appears in three places: on the product detail page, in the product listing/catalog grid, and on the cart page for any cart item whose associated product has fallen below the threshold. The `stockWarning` boolean field is computed on every read from the current `on_hand_qty` value — it is not stored separately. When a shopper views their cart, if any product has since dropped below the threshold, the cart page renders the warning inline next to that item to prompt action before checkout.

---

## 5. How are inactive profiles anonymized — what counts as an open dispute?

**Question:** The system anonymizes shopper profiles after 24 months of inactivity, but profiles tied to open disputes are exempt. What defines "inactive" and what counts as an "open dispute"?

**Answer:** **Inactivity** means no login event, no new order, no cart modification, and no new interaction event recorded for that user in the preceding 24 calendar months.

**An open dispute** is defined as the existence of any of the following records linked to the user:
- An order in status `PENDING`, `CONFIRMED`, or `READY_FOR_PICKUP` (order not yet completed or cancelled)
- A refund in status `PENDING` (refund initiated but not yet completed or failed)

If any such record exists, the anonymization job skips that user entirely, regardless of how long they have been inactive. The job re-evaluates the user on every subsequent scheduled run. When a profile is anonymized, `username` and `password_hash` are replaced with deterministic markers (`anon_{id}` and a random irreversible hash respectively), `users.anonymized` is set to `true`, and `users.anonymized_at` is recorded. Behavior events are dissociated by setting their `user_id` to NULL so they remain usable in aggregate for the recommendation engine without being traceable to the individual.

---

## 6. What format is the receipt timestamp in?

**Question:** The project requires a transaction timestamp on purchase confirmations and receipts. What format is used?

**Answer:** The transaction timestamp is recorded in **12-hour time format** with AM/PM designation. The format is: `MM/DD/YYYY hh:mm:ss AM|PM`, for example `04/18/2026 02:35:10 PM`. This format is stored as a formatted string in the order confirmation response payload under the `transactionTimestamp` field and is displayed verbatim on the purchase confirmation screen and printable receipt. The underlying database column stores the timestamp in UTC as a standard MySQL `DATETIME` type; the 12-hour format is applied in the service layer when constructing the response.

---

## 7. How are replay attacks detected and rejected?

**Question:** An attacker could capture a valid signed API request and re-submit it. How does the system detect and reject replayed requests?

**Answer:** Every API request must include two headers: `X-Timestamp` (Unix epoch seconds at request time) and `X-Nonce` (a unique UUID generated for this request). The server applies two checks. First, it validates the **timestamp window**: if the value of `X-Timestamp` is more than 5 minutes in the past or more than 5 minutes in the future relative to the server clock, the request is rejected with HTTP 400. Second, it validates the **nonce uniqueness**: used nonces are stored in MySQL with the timestamp they were received, and a unique constraint prevents the same nonce from being accepted twice. Nonce records are retained for 10 minutes and then purged by a maintenance job. A captured request replayed within the 5-minute window will be rejected because the nonce has already been recorded. A captured request replayed after 5 minutes will be rejected by the timestamp check. Both checks together make replay attacks infeasible.

---

## 8. What actions trigger an audit log entry?

**Question:** The project requires immutable audit logs for "critical actions." Which specific events always produce an audit log entry?

**Answer:** The following actions always produce an entry in the `audit_logs` table:

- Failed login attempts (every failure, recording the username attempted)
- Successful logins
- Logouts (explicit)
- Account lockout events (triggered after 5 failures)
- All refund initiations (by staff or admin)
- All feature flag changes (enable or disable, by admin)
- All operational threshold changes (by admin)
- Any configuration or settings update
- Sensitive data reads by Staff or Admin roles (compliance reports, transaction lookups)
- Backup initiation events
- Restore operations (start and completion)
- User role changes (admin assigning or removing roles)
- Account unlock by admin

Each audit log entry records: actor user ID, actor role at time of action, action type, target entity type and ID, a JSON detail blob with context, the requester's IP address, a request correlation ID, and the UTC timestamp.

---

## 9. How are distributed locks implemented without external dependencies like Redis?

**Question:** Distributed locks are required to prevent double payment posting under concurrency. How is this achieved in a system that must run entirely offline without Redis, ZooKeeper, or any external broker?

**Answer:** MySQL's built-in **advisory locking** mechanism (`GET_LOCK` / `RELEASE_LOCK`) is used. Before processing a payment write, the backend calls `SELECT GET_LOCK('payment:{order_id}', 5)` via a native JDBC query, where `order_id` is the target order's ID and `5` is the timeout in seconds. MySQL's advisory locks are session-scoped and mutually exclusive: if another database connection already holds the lock for the same key, the `GET_LOCK` call blocks until either the lock is released or the timeout expires. If `GET_LOCK` returns `0` (timeout) or `NULL` (error), the payment request returns HTTP 409. If `GET_LOCK` returns `1`, the payment write proceeds, and `RELEASE_LOCK` is called in a `finally` block to guarantee release even on exception. This requires no external service, only the MySQL instance already in use.

---

## 10. What happens if the nightly recommendation refresh job fails?

**Question:** The recommendation engine runs a scheduled refresh at 2:00 AM. If this job encounters an error, what is the fallback behavior?

**Answer:** The nightly job processes shoppers one at a time in a try-catch loop. If the job fails for a specific shopper (due to a database error, computation error, or timeout), the error is logged to `audit_logs` with the shopper's user ID and the exception detail, and the job continues to the next shopper. That shopper retains their previously cached recommendations in the `recommendations` table, which may be stale (older than 60 minutes) but remain visible to the shopper rather than returning an empty list. If the entire job fails to start (e.g., scheduler error), the same stale-cache fallback applies. On the next API call for recommendations from a shopper whose cache is expired, the system can optionally trigger an on-demand recomputation inline (subject to a configurable feature flag). Failed job runs are surfaced in the compliance report audit section so administrators can identify persistent failures.

---

## 11. How does the idempotency key prevent double order creation?

**Question:** A shopper's checkout request might be submitted twice due to a network timeout or double-tap. How does the idempotency key prevent two separate orders from being created?

**Answer:** When the shopper submits a checkout request, their client generates a UUID and sends it as `idempotencyKey` in the request body. The `orders` table has a unique constraint on the `idempotency_key` column. The service layer attempts to insert a new order row with the provided key. If the INSERT succeeds, a new order is created and the response is HTTP 201. If the INSERT fails with a unique constraint violation (the key already exists), the service fetches the existing order by that key and returns it with HTTP 200, along with the original receipt number and confirmation details. This means the shopper's client receives a successful response whether the order was just created or was already created by a previous attempt, with no duplicate order in the database. The same mechanism applies to payments via `payments.idempotency_key`.

---

## 12. What happens to cart items when a product goes out of stock?

**Question:** If a product's on-hand quantity drops to zero after a shopper has already added it to their cart, what behavior does the shopper experience?

**Answer:** Cart items are not automatically removed when a product goes out of stock. The cart item remains in the `cart_items` table so the shopper does not lose their selection. However, two behaviors change. First, when the shopper views their cart (`GET /api/cart`), items whose product has `on_hand_qty < 2` display the `stockWarning: true` flag; items with `on_hand_qty = 0` are flagged as unavailable in the cart view response. Second, at checkout time (`POST /api/orders`), the service validates current stock for every cart item before creating the order. If any item has insufficient on-hand quantity to satisfy the requested cart quantity, the order is rejected with HTTP 422 and an error body listing the out-of-stock items by product ID and name. The shopper is expected to remove those items from the cart and attempt checkout again. Cart items are never silently removed by the system.
