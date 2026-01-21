#!/bin/bash

# Load the functions from monitor-queues.sh
source monitor-queues.sh 2>/dev/null

# Or define them here
ENDPOINT_URL="http://localhost:4566"
REGION="us-east-1"
USE_DOCKER=true
REFRESH_INTERVAL=2

CYAN='\033[0;36m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

QUEUES=("validation-service-queue")

get_queue_url() {
    local queue_name=$1
    local queue_url
    
    if [ "$USE_DOCKER" = true ]; then
        queue_url=$(docker exec localstack awslocal sqs get-queue-url --queue-name "${queue_name}" --output json 2>/dev/null | grep -o '"QueueUrl"[^"]*"[^"]*' | cut -d'"' -f4)
    fi
    
    if [ -z "$queue_url" ]; then
        queue_url="${ENDPOINT_URL}/000000000000/${queue_name}"
    fi
    
    echo "$queue_url"
}

get_queue_stats() {
    local queue_url=$1
    local queue_name=$2
    
    local attributes
    if [ "$USE_DOCKER" = true ]; then
        attributes=$(docker exec localstack awslocal sqs get-queue-attributes \
            --queue-url "${queue_url}" \
            --attribute-names ApproximateNumberOfMessages,ApproximateNumberOfMessagesNotVisible,ApproximateNumberOfMessagesDelayed,ApproximateAgeOfOldestMessage \
            2>/dev/null || echo "{}")
    fi
    
    # Compact JSON to single line for consistent parsing
    attributes=$(echo "$attributes" | tr -d '\n' | tr -s ' ')
    
    if [ "$attributes" = "{}" ]; then
        echo "0|0|0|-"
        return
    fi
    
    local available=$(echo "$attributes" | grep -o '"ApproximateNumberOfMessages"[^,}]*' | grep -o '[0-9]*' || echo "0")
    local in_flight=$(echo "$attributes" | grep -o '"ApproximateNumberOfMessagesNotVisible"[^,}]*' | grep -o '[0-9]*' || echo "0")
    local delayed=$(echo "$attributes" | grep -o '"ApproximateNumberOfMessagesDelayed"[^,}]*' | grep -o '[0-9]*' || echo "0")
    local age=$(echo "$attributes" | grep -o '"ApproximateAgeOfOldestMessage"[^,}]*' | grep -o '[0-9]*' || echo "-")
    
    echo "${available}|${in_flight}|${delayed}|${age}"
}

get_status() {
    local available=$1
    local in_flight=$2
    
    if [ "$available" -gt 0 ] || [ "$in_flight" -gt 0 ]; then
        echo -e "${YELLOW}⚡ Active${NC}"
    else
        echo -e "${GREEN}✓ Idle${NC}"
    fi
}

echo "Testing monitor for 1 queue..."
for queue in "${QUEUES[@]}"; do
    echo "Queue: $queue"
    queue_url=$(get_queue_url "$queue")
    echo "  URL: $queue_url"
    stats=$(get_queue_stats "$queue_url" "$queue")
    echo "  Stats: $stats"
    IFS='|' read -r available in_flight delayed age <<< "$stats"
    echo "  Parsed: available=$available, in_flight=$in_flight, delayed=$delayed, age=$age"
    status=$(get_status "$available" "$in_flight")
    echo "  Status: $status"
done
