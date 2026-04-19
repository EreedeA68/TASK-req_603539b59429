# MeridianMart

The application source, Dockerfile, Docker Compose files, database schema, and full documentation live in the [`meridianmart/`](./meridianmart/) subdirectory.

See [`meridianmart/README.md`](./meridianmart/README.md) for setup, running, and testing instructions.

## Quick Start

```bash
cd meridianmart
cp .env.example .env   # fill in JWT_SECRET, AES_KEY, REQUEST_SIGNING_SECRET
docker compose up --build
```

## Running Tests

```bash
./run_tests.sh
```
