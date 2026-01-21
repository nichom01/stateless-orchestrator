#!/bin/bash

# Bulk submit OrderCreated events from test file using the bulk upload API
# Usage: ./submit-bulk-orders.sh [test-file] [base-url]

set -e

TEST_FILE="${1:-examples/test-orders-2500.jsonl}"
BASE_URL="${2:-http://localhost:8080}"
BULK_ENDPOINT="${BASE_URL}/api/orchestrator/events/bulk-ndjson"

# Colors
CYAN='\033[0;36m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

# Check if file exists
if [ ! -f "$TEST_FILE" ]; then
    echo -e "${RED}Error: Test file not found: ${TEST_FILE}${NC}"
    exit 1
fi

# Count lines
TOTAL=$(wc -l < "$TEST_FILE" | tr -d ' ')
echo -e "${CYAN}Submitting ${TOTAL} events using bulk upload API${NC}"
echo -e "${CYAN}Endpoint: ${BULK_ENDPOINT}${NC}"
echo ""

# Submit events using bulk API
START_TIME=$(date +%s)

RESPONSE=$(curl -X POST "$BULK_ENDPOINT" \
  -H "Content-Type: text/plain" \
  --data-binary @"$TEST_FILE" \
  -s -w "\n%{http_code}")

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

# Extract HTTP status code (last line)
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
# Extract JSON response (all but last line)
JSON_RESPONSE=$(echo "$RESPONSE" | head -n -1)

if [ "$HTTP_CODE" -eq 202 ] || [ "$HTTP_CODE" -eq 200 ]; then
    # Parse response if jq is available
    if command -v jq &> /dev/null; then
        SUCCESSFUL=$(echo "$JSON_RESPONSE" | jq -r '.successful // 0')
        FAILED=$(echo "$JSON_RESPONSE" | jq -r '.failed // 0')
        API_DURATION=$(echo "$JSON_RESPONSE" | jq -r '.durationMs // 0')
        
        echo ""
        echo -e "${GREEN}✓ Bulk upload completed${NC}"
        echo -e "${CYAN}Total: ${TOTAL} events${NC}"
        echo -e "${GREEN}Successful: ${SUCCESSFUL}${NC}"
        if [ "$FAILED" -gt 0 ]; then
            echo -e "${RED}Failed: ${FAILED}${NC}"
        fi
        echo -e "${CYAN}API processing time: ${API_DURATION}ms${NC}"
        echo -e "${CYAN}Total time (including network): ${DURATION}s${NC}"
        
        if [ "$DURATION" -gt 0 ]; then
            echo -e "${CYAN}Effective rate: $((TOTAL / DURATION)) events/second${NC}"
        fi
    else
        echo ""
        echo -e "${GREEN}✓ Submitted ${TOTAL} events in ${DURATION} seconds${NC}"
        echo -e "${YELLOW}Install jq for detailed response parsing${NC}"
        echo "$JSON_RESPONSE"
    fi
else
    echo -e "${RED}Error: HTTP ${HTTP_CODE}${NC}"
    echo "$JSON_RESPONSE"
    exit 1
fi

echo ""
echo -e "${YELLOW}Tip: Monitor queues with: ./scripts/monitor-queues.sh${NC}"
