#!/bin/bash

# Run performance tests locally
# Usage: ./run-perf-test-local.sh [test-type] [options]
#   test-type: load, stress, or spike (default: load)

set -e

TEST_TYPE="${1:-load}"
COMPOSE_FILE="docker-compose.perf.yml"
RESULTS_DIR="perf-tests/results"

# Colors
CYAN='\033[0;36m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${CYAN}Performance Test Runner${NC}"
echo -e "${CYAN}======================${NC}"
echo ""

# Validate test type
if [[ ! "$TEST_TYPE" =~ ^(load|stress|spike)$ ]]; then
    echo -e "${RED}Error: Invalid test type: ${TEST_TYPE}${NC}"
    echo "Valid types: load, stress, spike"
    exit 1
fi

# Create results directory
mkdir -p "$RESULTS_DIR"

# Start services
echo -e "${CYAN}Starting performance test environment...${NC}"
docker-compose -f "$COMPOSE_FILE" up -d localstack prometheus

echo -e "${YELLOW}Waiting for LocalStack...${NC}"
sleep 10

# Build and start orchestrator
echo -e "${CYAN}Building orchestrator...${NC}"
docker-compose -f "$COMPOSE_FILE" build orchestrator

echo -e "${CYAN}Starting orchestrator...${NC}"
docker-compose -f "$COMPOSE_FILE" up -d orchestrator

# Wait for orchestrator to be healthy
echo -e "${YELLOW}Waiting for orchestrator to be healthy...${NC}"
timeout 120 bash -c 'until docker exec perf-orchestrator curl -f http://localhost:8080/actuator/health > /dev/null 2>&1; do sleep 2; done' || {
    echo -e "${RED}✗ Orchestrator failed to become healthy${NC}"
    docker-compose -f "$COMPOSE_FILE" logs orchestrator
    exit 1
}

echo -e "${GREEN}✓ Orchestrator is healthy${NC}"
echo ""

# Generate test data if needed
if [ ! -f "perf-tests/test-data/test-data-10k.jsonl" ]; then
    echo -e "${CYAN}Generating test data...${NC}"
    node perf-tests/generate-test-data.js 10000 perf-tests/test-data/test-data-10k.jsonl
fi

# Determine test script
TEST_SCRIPT="/scripts/${TEST_TYPE}-test.js"
RESULTS_FILE="${RESULTS_DIR}/${TEST_TYPE}-test-results.json"
SUMMARY_FILE="${RESULTS_DIR}/${TEST_TYPE}-test-summary.json"

echo -e "${CYAN}Running ${TEST_TYPE} test...${NC}"
echo -e "${CYAN}Results will be saved to: ${RESULTS_DIR}${NC}"
echo ""

# Run k6 test
docker-compose -f "$COMPOSE_FILE" run --rm \
    -e ORCHESTRATOR_URL=http://orchestrator:8080 \
    -e EVENTS_PER_REQUEST=100 \
    k6 run \
    --out json="$RESULTS_FILE" \
    --summary-export="$SUMMARY_FILE" \
    "$TEST_SCRIPT" || {
    echo -e "${YELLOW}⚠ Test completed with errors (check results)${NC}"
}

# Generate HTML report
if [ -f "$RESULTS_FILE" ]; then
    echo ""
    echo -e "${CYAN}Generating HTML report...${NC}"
    node perf-tests/scripts/generate-report.js \
        --input "$RESULTS_FILE" \
        --output "${RESULTS_DIR}/${TEST_TYPE}-test-report.html" \
        --type "$TEST_TYPE" || {
        echo -e "${YELLOW}⚠ Report generation failed${NC}"
    }
fi

# Collect logs
echo ""
echo -e "${CYAN}Collecting logs...${NC}"
docker logs perf-orchestrator > "${RESULTS_DIR}/orchestrator-logs-${TEST_TYPE}.txt" 2>&1 || true

# Collect queue statistics
if command -v ./scripts/queue-stats.sh &> /dev/null; then
    ./scripts/queue-stats.sh > "${RESULTS_DIR}/queue-stats-${TEST_TYPE}.txt" 2>&1 || true
fi

echo ""
echo -e "${GREEN}✓ Test completed!${NC}"
echo -e "${CYAN}Results:${NC}"
echo -e "  - JSON: ${RESULTS_FILE}"
echo -e "  - Summary: ${SUMMARY_FILE}"
echo -e "  - Report: ${RESULTS_DIR}/${TEST_TYPE}-test-report.html"
echo -e "  - Logs: ${RESULTS_DIR}/orchestrator-logs-${TEST_TYPE}.txt"
echo ""
echo -e "${YELLOW}To view results:${NC}"
echo -e "  open ${RESULTS_DIR}/${TEST_TYPE}-test-report.html"
echo ""
echo -e "${YELLOW}To cleanup:${NC}"
echo -e "  docker-compose -f ${COMPOSE_FILE} down"
