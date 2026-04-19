#!/usr/bin/env bash
set -euo pipefail

echo "=========================================="
echo "  MeridianMart Test Runner"
echo "=========================================="

COMPOSE_FILE="docker-compose.test.yml"
APP_CONTAINER="meridianmart-app-test"
COVERAGE_DIR="./coverage-report"
MAX_WAIT=120
HEALTH_URL="http://localhost:8081/actuator/health"

cleanup() {
    echo "Cleaning up test environment..."
    docker-compose -f "$COMPOSE_FILE" down -v 2>/dev/null || true
}
trap cleanup EXIT

# Step 1: Build test Docker image
echo ""
echo "[1/6] Building test Docker image..."
docker-compose -f "$COMPOSE_FILE" build --no-cache

# Step 2: Start docker-compose test environment
echo ""
echo "[2/6] Starting test environment (app + db)..."
docker-compose -f "$COMPOSE_FILE" up -d

# Step 3: Wait for app health check
echo ""
echo "[3/6] Waiting for app to be healthy (max ${MAX_WAIT}s)..."
elapsed=0
until curl -sf "$HEALTH_URL" > /dev/null 2>&1; do
    if [ "$elapsed" -ge "$MAX_WAIT" ]; then
        echo "ERROR: App did not become healthy within ${MAX_WAIT}s"
        docker-compose -f "$COMPOSE_FILE" logs app-test
        exit 1
    fi
    echo "  Waiting... (${elapsed}s elapsed)"
    sleep 5
    elapsed=$((elapsed + 5))
done
echo "  App is healthy!"

# Step 4: Run Maven tests
echo ""
echo "[4/7] Running Maven tests inside container..."
docker exec "$APP_CONTAINER" mvn verify -Dspring.profiles.active=test \
    -Dmaven.test.failure.ignore=false 2>&1
TEST_EXIT=$?

# Step 4b: Run frontend JavaScript unit tests
echo ""
echo "[4b/7] Running frontend JS unit tests (Jest)..."
docker exec "$APP_CONTAINER" bash -c "cd /build && npm test" 2>&1
JS_EXIT=$?
if [ "$JS_EXIT" -ne 0 ]; then
    echo "ERROR: Frontend JS tests failed (exit code: $JS_EXIT)"
    TEST_EXIT=$JS_EXIT
fi

# Step 5: Copy JaCoCo HTML report
echo ""
echo "[5/7] Copying JaCoCo coverage report..."
mkdir -p "$COVERAGE_DIR"
docker cp "${APP_CONTAINER}:/build/target/site/jacoco/." "$COVERAGE_DIR/" 2>/dev/null || {
    echo "WARNING: Could not copy JaCoCo report (may not have been generated)"
}

# Step 6: Print coverage summary
echo ""
echo "[6/7] Coverage summary:"
if [ -f "$COVERAGE_DIR/index.html" ]; then
    echo "  Coverage report available at: $COVERAGE_DIR/index.html"
    # Extract coverage from jacoco.xml if available
    JACOCO_XML="${APP_CONTAINER}:/build/target/site/jacoco/jacoco.xml"
    docker exec "$APP_CONTAINER" bash -c "
        if [ -f /build/target/site/jacoco/jacoco.xml ]; then
            grep -oP 'type=\"LINE\"[^/]*/>' /build/target/site/jacoco/jacoco.xml | tail -1 | \
            grep -oP '(?<=covered=\")\d+|(?<=missed=\")\d+' | \
            awk 'NR==1{covered=\$1} NR==2{missed=\$1; total=covered+missed; pct=covered/total*100; printf \"  Line Coverage: %.1f%% (%d/%d lines)\\n\", pct, covered, total}'
        fi
    " 2>/dev/null || true
else
    echo "  No coverage report found."
fi

echo ""
echo "[7/7] Test summary:"
echo "=========================================="
if [ "$TEST_EXIT" -eq 0 ]; then
    echo "  ALL TESTS PASSED (Maven + Jest)"
    echo "  Coverage report: $COVERAGE_DIR/index.html"
    echo "=========================================="
    exit 0
else
    echo "  TESTS FAILED (exit code: $TEST_EXIT)"
    echo "=========================================="
    exit 1
fi
