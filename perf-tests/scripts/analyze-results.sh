#!/bin/bash

# Analyze performance test results
# Usage: ./analyze-results.sh [results-dir]

set -e

RESULTS_DIR="${1:-perf-tests/results}"

# Colors
CYAN='\033[0;36m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${CYAN}Performance Test Results Analysis${NC}"
echo -e "${CYAN}===================================${NC}"
echo ""

if [ ! -d "$RESULTS_DIR" ]; then
    echo -e "${RED}Error: Results directory not found: ${RESULTS_DIR}${NC}"
    exit 1
fi

# Check for k6 results
if [ -f "$RESULTS_DIR/load-test-summary.json" ]; then
    echo -e "${GREEN}Load Test Results:${NC}"
    if command -v jq &> /dev/null; then
        jq -r '
            "  Total Requests: " + (.metrics.http_reqs.values.count | tostring) +
            "\n  Failed Requests: " + (.metrics.http_req_failed.values.rate | tostring) +
            "\n  Avg Duration: " + (.metrics.http_req_duration.values.avg | tostring) + "ms" +
            "\n  P95 Duration: " + (.metrics.http_req_duration.values.p95 | tostring) + "ms" +
            "\n  P99 Duration: " + (.metrics.http_req_duration.values.p99 | tostring) + "ms"
        ' "$RESULTS_DIR/load-test-summary.json" 2>/dev/null || echo "  (Unable to parse JSON)"
    else
        echo "  Install jq for detailed analysis"
    fi
    echo ""
fi

if [ -f "$RESULTS_DIR/stress-test-summary.json" ]; then
    echo -e "${GREEN}Stress Test Results:${NC}"
    if command -v jq &> /dev/null; then
        jq -r '
            "  Total Requests: " + (.metrics.http_reqs.values.count | tostring) +
            "\n  Failed Requests: " + (.metrics.http_req_failed.values.rate | tostring) +
            "\n  Avg Duration: " + (.metrics.http_req_duration.values.avg | tostring) + "ms" +
            "\n  P95 Duration: " + (.metrics.http_req_duration.values.p95 | tostring) + "ms" +
            "\n  P99 Duration: " + (.metrics.http_req_duration.values.p99 | tostring) + "ms"
        ' "$RESULTS_DIR/stress-test-summary.json" 2>/dev/null || echo "  (Unable to parse JSON)"
    else
        echo "  Install jq for detailed analysis"
    fi
    echo ""
fi

# Check for HTML reports
if [ -f "$RESULTS_DIR/load-test-report.html" ]; then
    echo -e "${GREEN}✓ Load test report: ${RESULTS_DIR}/load-test-report.html${NC}"
fi

if [ -f "$RESULTS_DIR/stress-test-report.html" ]; then
    echo -e "${GREEN}✓ Stress test report: ${RESULTS_DIR}/stress-test-report.html${NC}"
fi

# Check for queue statistics
if [ -f "$RESULTS_DIR/queue-stats.txt" ]; then
    echo -e "${CYAN}Queue Statistics:${NC}"
    cat "$RESULTS_DIR/queue-stats.txt"
    echo ""
fi

# Check for orchestrator logs
if [ -f "$RESULTS_DIR/orchestrator-logs.txt" ]; then
    LOG_SIZE=$(wc -l < "$RESULTS_DIR/orchestrator-logs.txt")
    echo -e "${CYAN}Orchestrator Logs: ${LOG_SIZE} lines${NC}"
fi

echo -e "${GREEN}Analysis complete!${NC}"
