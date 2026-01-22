#!/bin/bash

# Monitor queue depths during performance test
# Usage: ./monitor-queues-during-test.sh [interval-seconds] [duration-seconds]

set -e

INTERVAL="${1:-5}"  # Default 5 seconds
DURATION="${2:-300}"  # Default 5 minutes
OUTPUT_FILE="${3:-perf-tests/results/queue-monitor.csv}"

# Colors
CYAN='\033[0;36m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

mkdir -p "$(dirname "$OUTPUT_FILE")"

# CSV header
echo "timestamp,queue_name,messages_available,messages_in_flight" > "$OUTPUT_FILE"

echo -e "${CYAN}Monitoring queues every ${INTERVAL}s for ${DURATION}s...${NC}"
echo -e "${CYAN}Output: ${OUTPUT_FILE}${NC}"

START_TIME=$(date +%s)
END_TIME=$((START_TIME + DURATION))
ITERATION=0

while [ $(date +%s) -lt $END_TIME ]; do
    ITERATION=$((ITERATION + 1))
    TIMESTAMP=$(date +%s)
    
    # Get queue statistics
    if command -v ./scripts/queue-stats.sh &> /dev/null; then
        # Parse queue stats and append to CSV
        ./scripts/queue-stats.sh --json 2>/dev/null | \
            jq -r '.[] | "\(.timestamp // "'$TIMESTAMP'"),\(.name),\(.messagesAvailable // 0),\(.messagesInFlight // 0)"' \
            >> "$OUTPUT_FILE" 2>/dev/null || true
    fi
    
    if [ $((ITERATION % 10)) -eq 0 ]; then
        echo -e "${YELLOW}[$(date +%H:%M:%S)] Monitoring... (${ITERATION} iterations)${NC}"
    fi
    
    sleep "$INTERVAL"
done

echo -e "${GREEN}✓ Queue monitoring completed${NC}"
echo -e "${CYAN}Results saved to: ${OUTPUT_FILE}${NC}"
