#!/bin/bash

# Wait for services to be ready before running performance tests
# Usage: ./wait-for-services.sh [orchestrator-url] [timeout-seconds]

set -e

ORCHESTRATOR_URL="${1:-http://localhost:8080}"
TIMEOUT="${2:-120}"

# Colors
CYAN='\033[0;36m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${CYAN}Waiting for services to be ready...${NC}"
echo -e "${CYAN}Orchestrator URL: ${ORCHESTRATOR_URL}${NC}"
echo -e "${CYAN}Timeout: ${TIMEOUT}s${NC}"
echo ""

START_TIME=$(date +%s)
ELAPSED=0

# Wait for orchestrator
echo -e "${YELLOW}Waiting for orchestrator...${NC}"
while [ $ELAPSED -lt $TIMEOUT ]; do
    if curl -sf "${ORCHESTRATOR_URL}/actuator/health" > /dev/null 2>&1; then
        HEALTH_RESPONSE=$(curl -sf "${ORCHESTRATOR_URL}/actuator/health" 2>/dev/null || echo "")
        if echo "$HEALTH_RESPONSE" | grep -q '"status":"UP"' || echo "$HEALTH_RESPONSE" | grep -q 'UP'; then
            echo -e "${GREEN}✓ Orchestrator is healthy${NC}"
            break
        fi
    fi
    
    sleep 2
    ELAPSED=$(($(date +%s) - START_TIME))
    if [ $((ELAPSED % 10)) -eq 0 ]; then
        echo -e "${YELLOW}  Still waiting... (${ELAPSED}s elapsed)${NC}"
    fi
done

if [ $ELAPSED -ge $TIMEOUT ]; then
    echo -e "${RED}✗ Timeout waiting for orchestrator${NC}"
    exit 1
fi

# Wait for LocalStack (if running)
if curl -sf "http://localhost:4566/_localstack/health" > /dev/null 2>&1; then
    echo -e "${GREEN}✓ LocalStack is healthy${NC}"
else
    echo -e "${YELLOW}⚠ LocalStack not detected (may not be running)${NC}"
fi

echo ""
echo -e "${GREEN}All services are ready!${NC}"
