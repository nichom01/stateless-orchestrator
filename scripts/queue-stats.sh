#!/bin/bash

# One-time snapshot of LocalStack SQS queue statistics
# Usage: ./queue-stats.sh [--json]

set -e

ENDPOINT_URL="${LOCALSTACK_ENDPOINT:-http://localhost:4566}"
REGION="${AWS_REGION:-us-east-1}"
OUTPUT_FORMAT="${1:-table}"

# Colors
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

# Queue names from init-localstack.sh
QUEUES=(
    "orchestrator-input"
    "validation-service-queue"
    "inventory-service-queue"
    "notification-service-queue"
    "express-payment-service-queue"
    "fraud-check-service-queue"
    "payment-service-queue"
    "order-cancellation-service-queue"
    "inventory-rollback-service-queue"
    "payment-retry-service-queue"
    "fulfillment-service-queue"
    "digital-delivery-service-queue"
    "shipping-service-queue"
)

# Function to get queue URL from LocalStack/AWS
get_queue_url() {
    local queue_name=$1
    local queue_url
    
    if [ "$USE_DOCKER" = true ]; then
        queue_url=$(docker exec localstack awslocal sqs get-queue-url --queue-name "${queue_name}" --output json 2>/dev/null | grep -o '"QueueUrl"[^"]*"[^"]*' | cut -d'"' -f4)
    else
        queue_url=$(aws --endpoint-url="${ENDPOINT_URL}" --region="${REGION}" sqs get-queue-url --queue-name "${queue_name}" --output json 2>/dev/null | grep -o '"QueueUrl"[^"]*"[^"]*' | cut -d'"' -f4)
    fi
    
    # Fallback to constructed URL if get-queue-url fails
    if [ -z "$queue_url" ]; then
        queue_url="${ENDPOINT_URL}/000000000000/${queue_name}"
    fi
    
    echo "$queue_url"
}

# Determine which AWS command to use
USE_DOCKER=false
AWS_CMD="aws"

if ! command -v aws &> /dev/null; then
    # AWS CLI not found on host, try using docker exec with localstack
    if docker ps | grep -q localstack; then
        USE_DOCKER=true
        AWS_CMD="docker exec localstack awslocal"
        echo -e "${YELLOW}Note: Using LocalStack's awslocal via Docker (AWS CLI not found on host)${NC}"
    else
        echo -e "${RED}Error: AWS CLI is not installed and LocalStack container is not running${NC}"
        echo -e "${YELLOW}Install AWS CLI with: brew install awscli${NC}"
        echo -e "${YELLOW}Or start LocalStack: docker-compose up -d${NC}"
        exit 1
    fi
fi

# Function to get queue attributes
get_queue_stats() {
    local queue_url=$1
    local queue_name=$2
    
    # Get queue attributes using appropriate command
    local attributes
    if [ "$USE_DOCKER" = true ]; then
        # Using docker exec - awslocal already points to LocalStack
        attributes=$(docker exec localstack awslocal sqs get-queue-attributes \
            --queue-url "${queue_url}" \
            --attribute-names All \
            2>/dev/null || echo "{}")
    else
        # Using AWS CLI on host
        attributes=$(aws --endpoint-url="${ENDPOINT_URL}" \
            --region="${REGION}" \
            sqs get-queue-attributes \
            --queue-url "${queue_url}" \
            --attribute-names All \
            2>/dev/null || echo "{}")
    fi
    
    if [ "$attributes" = "{}" ]; then
        echo "{}"
        return
    fi
    
    # Compact JSON to single line (remove newlines and extra spaces)
    echo "$attributes" | tr -d '\n' | tr -s ' '
}

# Check if LocalStack is running
if ! curl -s "${ENDPOINT_URL}/_localstack/health" > /dev/null 2>&1; then
    echo -e "${RED}Error: Cannot connect to LocalStack at ${ENDPOINT_URL}${NC}"
    exit 1
fi

# Collect stats
declare -a STATS_ARRAY

for queue in "${QUEUES[@]}"; do
    queue_url=$(get_queue_url "$queue")
    stats=$(get_queue_stats "$queue_url" "$queue")
    
    if [ "$stats" != "{}" ]; then
        STATS_ARRAY+=("$queue|$stats")
    fi
done

# Output based on format
if [ "$OUTPUT_FORMAT" = "--json" ]; then
    # JSON output
    echo "["
    for i in "${!STATS_ARRAY[@]}"; do
        IFS='|' read -r queue_name stats <<< "${STATS_ARRAY[$i]}"
        echo -n "  {"
        echo -n "\"queueName\": \"$queue_name\","
        echo -n "\"queueUrl\": \"$(get_queue_url "$queue_name")\","
        
        # Extract values from stats JSON
        available=$(echo "$stats" | grep -o '"ApproximateNumberOfMessages"[^,}]*' | grep -o '[0-9]*' || echo "0")
        in_flight=$(echo "$stats" | grep -o '"ApproximateNumberOfMessagesNotVisible"[^,}]*' | grep -o '[0-9]*' || echo "0")
        delayed=$(echo "$stats" | grep -o '"ApproximateNumberOfMessagesDelayed"[^,}]*' | grep -o '[0-9]*' || echo "0")
        age=$(echo "$stats" | grep -o '"ApproximateAgeOfOldestMessage"[^,}]*' | grep -o '[0-9]*' || echo "0")
        
        echo -n "\"available\": $available,"
        echo -n "\"inFlight\": $in_flight,"
        echo -n "\"delayed\": $delayed,"
        echo -n "\"oldestMessageAge\": $age"
        echo -n "}"
        if [ $i -lt $((${#STATS_ARRAY[@]} - 1)) ]; then
            echo ","
        else
            echo ""
        fi
    done
    echo "]"
else
    # Table output
    timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    
    echo -e "${CYAN}┌─────────────────────────────────────────────────────────────────────────────────┐${NC}"
    echo -e "${CYAN}│${NC}                    ${BLUE}LocalStack SQS Queue Statistics${NC}                              ${CYAN}│${NC}"
    echo -e "${CYAN}│${NC}                    ${BLUE}Snapshot: ${timestamp}${NC}                            ${CYAN}│${NC}"
    echo -e "${CYAN}└─────────────────────────────────────────────────────────────────────────────────┘${NC}"
    echo ""
    
    printf "%-35s %10s %10s %8s %8s\n" "QUEUE NAME" "AVAILABLE" "IN-FLIGHT" "DELAYED" "AGE(s)"
    echo "────────────────────────────────────────────────────────────────────────────────────────────────"
    
    for stats_entry in "${STATS_ARRAY[@]}"; do
        IFS='|' read -r queue_name stats <<< "$stats_entry"
        
        # Parse JSON stats
        available=$(echo "$stats" | grep -o '"ApproximateNumberOfMessages"[^,}]*' | grep -o '[0-9]*' || echo "0")
        in_flight=$(echo "$stats" | grep -o '"ApproximateNumberOfMessagesNotVisible"[^,}]*' | grep -o '[0-9]*' || echo "0")
        delayed=$(echo "$stats" | grep -o '"ApproximateNumberOfMessagesDelayed"[^,}]*' | grep -o '[0-9]*' || echo "0")
        age=$(echo "$stats" | grep -o '"ApproximateAgeOfOldestMessage"[^,}]*' | grep -o '[0-9]*' || echo "-")
        
        # Format queue name (truncate if too long)
        display_name="$queue_name"
        if [ ${#display_name} -gt 33 ]; then
            display_name="${display_name:0:30}..."
        fi
        
        printf "%-35s %10s %10s %8s %8s\n" "$display_name" "$available" "$in_flight" "$delayed" "$age"
    done
    
    # Summary
    total_available=0
    total_in_flight=0
    total_delayed=0
    
    for stats_entry in "${STATS_ARRAY[@]}"; do
        IFS='|' read -r queue_name stats <<< "$stats_entry"
        available=$(echo "$stats" | grep -o '"ApproximateNumberOfMessages"[^,}]*' | grep -o '[0-9]*' || echo "0")
        in_flight=$(echo "$stats" | grep -o '"ApproximateNumberOfMessagesNotVisible"[^,}]*' | grep -o '[0-9]*' || echo "0")
        delayed=$(echo "$stats" | grep -o '"ApproximateNumberOfMessagesDelayed"[^,}]*' | grep -o '[0-9]*' || echo "0")
        total_available=$((total_available + available))
        total_in_flight=$((total_in_flight + in_flight))
        total_delayed=$((total_delayed + delayed))
    done
    
    echo "────────────────────────────────────────────────────────────────────────────────────────────────"
    printf "%-35s %10s %10s %8s\n" "TOTAL" "$total_available" "$total_in_flight" "$total_delayed"
    echo ""
fi
