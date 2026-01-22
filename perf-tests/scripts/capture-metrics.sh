#!/bin/bash

# Capture metrics during performance testing
# Usage: ./capture-metrics.sh [duration] [output-dir]

set -e

DURATION="${1:-300}"  # Default 5 minutes
OUTPUT_DIR="${2:-perf-tests/results/metrics}"

# Colors
CYAN='\033[0;36m'
GREEN='\033[0;32m'
NC='\033[0m'

mkdir -p "$OUTPUT_DIR"

echo -e "${CYAN}Capturing metrics for ${DURATION} seconds...${NC}"

# Capture Prometheus metrics
if docker ps | grep -q perf-prometheus; then
    echo -e "${CYAN}Capturing Prometheus metrics...${NC}"
    
    # Query orchestrator metrics
    curl -s "http://localhost:9090/api/v1/query?query=up" > "$OUTPUT_DIR/prometheus-up.json" || true
    curl -s "http://localhost:9090/api/v1/query?query=jvm_memory_used_bytes" > "$OUTPUT_DIR/prometheus-memory.json" || true
    curl -s "http://localhost:9090/api/v1/query?query=jvm_memory_max_bytes" > "$OUTPUT_DIR/prometheus-memory-max.json" || true
    curl -s "http://localhost:9090/api/v1/query?query=process_cpu_usage" > "$OUTPUT_DIR/prometheus-cpu.json" || true
    
    # Query orchestrator custom metrics
    curl -s "http://localhost:9090/api/v1/query?query=orchestrator_events_processed_total" > "$OUTPUT_DIR/prometheus-events-processed.json" || true
    curl -s "http://localhost:9090/api/v1/query?query=orchestrator_events_routed_total" > "$OUTPUT_DIR/prometheus-events-routed.json" || true
    curl -s "http://localhost:9090/api/v1/query?query=orchestrator_routing_time_seconds" > "$OUTPUT_DIR/prometheus-routing-time.json" || true
fi

# Capture queue statistics
if command -v ./scripts/queue-stats.sh &> /dev/null; then
    echo -e "${CYAN}Capturing queue statistics...${NC}"
    ./scripts/queue-stats.sh > "$OUTPUT_DIR/queue-stats.txt" 2>&1 || true
fi

# Capture orchestrator health
if docker ps | grep -q perf-orchestrator; then
    echo -e "${CYAN}Capturing orchestrator health...${NC}"
    docker exec perf-orchestrator curl -s http://localhost:8080/actuator/health > "$OUTPUT_DIR/orchestrator-health.json" 2>&1 || true
    docker exec perf-orchestrator curl -s http://localhost:8080/actuator/metrics > "$OUTPUT_DIR/orchestrator-metrics.txt" 2>&1 || true
fi

# Capture container stats
if docker ps | grep -q perf-orchestrator; then
    echo -e "${CYAN}Capturing container statistics...${NC}"
    docker stats --no-stream perf-orchestrator > "$OUTPUT_DIR/container-stats.txt" 2>&1 || true
fi

echo -e "${GREEN}✓ Metrics captured to ${OUTPUT_DIR}${NC}"
