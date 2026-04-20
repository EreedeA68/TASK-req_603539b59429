# MeridianMart

A full-stack offline commerce platform built for internal store networks, providing a complete shopping and fulfillment loop for Shoppers, Staff, and Administrators.

## Architecture & Tech Stack

- **Frontend:** Thymeleaf (server-rendered), HTML, CSS, JS
- **Backend:** Java 17, Spring Boot 3, Maven
- **Database:** MySQL 8
- **Containerization:** Docker & Docker Compose

## Project Structure

```
meridianmart/
├── src/
│   ├── main/
│   │   ├── java/com/meridianmart/
│   │   │   ├── audit/           # Immutable audit logging
│   │   │   ├── config/          # Spring Security, exception handling
│   │   │   ├── controller/      # REST + page controllers
│   │   │   ├── dto/             # Data transfer objects
│   │   │   ├── model/           # JPA entity classes
│   │   │   ├── payment/         # Payment & distributed lock services
│   │   │   ├── recommendation/  # Collaborative filtering engine
│   │   │   ├── repository/      # Spring Data JPA repositories
│   │   │   ├── scheduler/       # Nightly jobs (recommendations, data retention)
│   │   │   ├── security/        # JWT, AES encryption, rate limiting, request signing
│   │   │   └── service/         # Business logic layer
│   │   └── resources/
│   │       ├── templates/       # Thymeleaf HTML templates
│   │       │   ├── login.html
│   │       │   ├── home.html
│   │       │   ├── product.html
│   │       │   ├── cart.html
│   │       │   ├── checkout.html
│   │       │   ├── confirmation.html
│   │       │   ├── orders.html
│   │       │   ├── notifications.html
│   │       │   ├── favorites.html
│   │       │   ├── staff/dashboard.html
│   │       │   └── admin/dashboard.html
│   │       ├── static/
│   │       │   ├── css/main.css
│   │       │   └── js/app.js
│   │       └── application.yml
│   └── test/
│       └── java/com/meridianmart/
│           ├── unit/            # Mockito service unit tests
│           ├── integration/     # MockMvc endpoint integration tests
│           ├── frontend/        # Selenium page component tests
│           └── e2e/             # Selenium full user journey tests
├── db/
│   └── init.sql                 # Schema + seed data
├── scripts/
│   ├── backup.sh                # Daily backup (runs in container)
│   └── restore.sh               # Restore with traceability
├── Dockerfile                   # Multi-stage build
├── docker-compose.yml           # Production environment
├── docker-compose.test.yml      # Test environment
├── .env.example                 # Environment variable template
├── run_tests.sh                 # Full test suite runner
└── README.md
```

## Prerequisites

**Docker (recommended):**
- Docker
- Docker Compose

**Without Docker:**
- Java 17+
- Maven 3.8+

## Running the Application

```bash
# 1. Copy environment configuration
cp meridianmart/.env.example meridianmart/.env

# 2. Build and start
docker-compose up --build -d

# 3. Access the application
# Visit http://localhost:8080 in your browser
```

The application will automatically initialize the database schema and seed data on first startup.

**Environment variables (configure in `.env` copied from `.env.example`):**

| Variable | Description |
|---|---|
| `DB_HOST` | MySQL host (default: `localhost`) |
| `DB_PORT` | MySQL port (default: `3306`) |
| `DB_NAME` | Database name |
| `DB_USER` | Database username |
| `DB_PASSWORD` | Database password |
| `JWT_SECRET` | Secret key for JWT signing (min 32 chars) |
| `REQUEST_SIGNING_SECRET` | Secret for HMAC request signing (min 32 chars) |
| `AES_KEY` | 32-byte key for AES-256 encryption |

## Running Without Docker (Maven + H2)

For quick local development without Docker, the application can be started with an embedded H2 in-memory database using the `h2` Spring profile.

**Prerequisites:** Java 17+, Maven 3.8+

```bash
cd meridianmart

# Start with the H2 dev profile (no MySQL or Docker needed)
mvn spring-boot:run -Dspring-boot.run.profiles=h2 \
  -Dspring-boot.run.jvmArguments="\
    -DJWT_SECRET=LocalDevSecretKeyForTestingOnly1234 \
    -DREQUEST_SIGNING_SECRET=LocalDevRequestSigningSecret12 \
    -DAES_KEY=LocalDevAES256KeyForTesting32B"
```

The H2 console is available at `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:meridianmart`).
Schema and seed data are loaded automatically from `db/init.sql` on startup.

> **Note:** H2 mode is for development only. The production deployment uses MySQL via Docker Compose.

## Smoke Verification

After the containers are healthy, confirm the application is operational:

```bash
# 1. Health endpoint must return {"status":"UP"}
curl -sf http://localhost:8080/actuator/health

# 2. Login page must respond HTTP 200
curl -sf -o /dev/null -w "HTTP %{http_code}\n" http://localhost:8080/login
# Expected: HTTP 200

# 3. Static assets must be served
curl -sf -o /dev/null -w "HTTP %{http_code}\n" http://localhost:8080/js/app.js
# Expected: HTTP 200
```

All three checks must pass before the application is considered operational.

## Testing

```bash
chmod +x run_tests.sh && ./run_tests.sh
```

The test runner will:
1. Build the test Docker image
2. Start the test environment (app + db)
3. Wait for the app health check to pass
4. Run all tests (unit, integration, frontend, e2e) inside Docker
5. Copy the JaCoCo HTML coverage report to `./coverage-report/`
6. Print the coverage summary
7. Exit 0 if all tests pass and coverage ≥ 90%

## Seeded Credentials

| Role      | Email                | Password       |
|-----------|----------------------|----------------|
| Admin     | admin@example.com    | AdminTest123!  |
| Staff     | staff@example.com    | StaffTest123!  |
| Shopper   | user@example.com     | UserTest123!   |
| Read-Only | guest@example.com    | GuestTest123!  |

## Key Features

### Security
- JWT authentication with role-based access control
- Brute-force protection: account locked for 15 minutes after 5 failed attempts
- API request signing with `X-Timestamp` and `X-Nonce` headers (5-minute replay window)
- Rate limiting: 60 requests/minute per user (Bucket4j)
- AES-256 encryption for payment tokens stored at rest
- Salted bcrypt password hashing
- Immutable audit logs for login failures, refunds, config changes

### Recommendation Engine
- Collaborative filtering using cosine similarity
- Requires ≥ 3 shared interactions before computing similarity
- Cold start: falls back to category popularity (last 30 days) + new arrivals
- Cache TTL: 60 minutes (stored in `recommendations` table)
- Nightly refresh at 2:00 AM via scheduled job

### Payment System
- Offline WeChat Pay-style POS confirmation recording
- Idempotent transactions via unique `idempotency_key`
- Distributed locking to prevent concurrent double-posting
- Supports: payment, refund, deposit/pre-authorization

### Notifications
- Auto-created on order status changes and pickup readiness
- Capped at 5 notifications per day per shopper

### Backup & Retention
- Daily full database backup retained for 30 days
- Restore script with traceability logging
- Inactive shopper profiles anonymized after 24 months (unless tied to open disputes)
