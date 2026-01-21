#!/bin/bash

# LocalStack SQS Queue Monitoring Dashboard
# Real-time monitoring of all SQS queues in LocalStack
# Updates automatically every 2 seconds

# Note: set -e removed to prevent script from exiting on non-critical errors

ENDPOINT_URL="${LOCALSTACK_ENDPOINT:-http://localhost:4566}"
REGION="${AWS_REGION:-us-east-1}"
REFRESH_INTERVAL="${REFRESH_INTERVAL:-2}"

# Colors for terminal output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

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

# Function to get queue attributes
get_queue_stats() {
    local queue_url=$1
    local queue_name=$2
    
    # Get queue attributes using appropriate command
    local attributes
    if [ "$USE_DOCKER" = true ]; then
        # Using docker exec - awslocal already points to LocalStack
        # Note: Using 'All' instead of specific attributes to avoid issues with some AWS CLI/LocalStack versions
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
    
    # Compact JSON to single line for consistent parsing
    attributes=$(echo "$attributes" | tr -d '\n' | tr -s ' ')
    
    if [ "$attributes" = "{}" ]; then
        echo "0|0|0|-"
        return
    fi
    
    # Parse JSON response (using grep/sed for portability)
    local available=$(echo "$attributes" | grep -o '"ApproximateNumberOfMessages"[^,}]*' | grep -o '[0-9]*' || echo "0")
    local in_flight=$(echo "$attributes" | grep -o '"ApproximateNumberOfMessagesNotVisible"[^,}]*' | grep -o '[0-9]*' || echo "0")
    local delayed=$(echo "$attributes" | grep -o '"ApproximateNumberOfMessagesDelayed"[^,}]*' | grep -o '[0-9]*' || echo "0")
    local age=$(echo "$attributes" | grep -o '"ApproximateAgeOfOldestMessage"[^,}]*' | grep -o '[0-9]*' || echo "-")
    
    echo "${available}|${in_flight}|${delayed}|${age}"
}

# Function to get status icon and color
get_status() {
    local available=$1
    local in_flight=$2
    
    if [ "$available" -gt 0 ] || [ "$in_flight" -gt 0 ]; then
        echo -e "${YELLOW}⚡ Active${NC}"
    else
        echo -e "${GREEN}✓ Idle${NC}"
    fi
}

# Function to display dashboard
display_dashboard() {
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    
    # Clear screen and move cursor to top
    clear
    
    # Header
    echo -e "${CYAN}┌─────────────────────────────────────────────────────────────────────────────────┐${NC}"
    echo -e "${CYAN}│${NC}                    ${BLUE}LocalStack SQS Queue Monitor${NC}                                  ${CYAN}│${NC}"
    echo -e "${CYAN}│${NC}                    ${BLUE}Last Updated: ${timestamp}${NC}                            ${CYAN}│${NC}"
    echo -e "${CYAN}└─────────────────────────────────────────────────────────────────────────────────┘${NC}"
    echo ""
    
    # Table header
    printf "%-35s %10s %10s %8s %8s %12s\n" "QUEUE NAME" "AVAILABLE" "IN-FLIGHT" "DELAYED" "AGE(s)" "STATUS"
    echo "────────────────────────────────────────────────────────────────────────────────────────────────"
    
    # Table rows
    for queue in "${QUEUES[@]}"; do
        local queue_url=$(get_queue_url "$queue")
        local stats=$(get_queue_stats "$queue_url" "$queue")
        
        IFS='|' read -r available in_flight delayed age <<< "$stats"
        local status=$(get_status "$available" "$in_flight")
        
        # Format queue name (truncate if too long)
        local display_name="$queue"
        if [ ${#display_name} -gt 33 ]; then
            display_name="${display_name:0:30}..."
        fi
        
        printf "%-35s %10s %10s %8s %8s " "$display_name" "$available" "$in_flight" "$delayed" "$age"
        echo -e "$status"
    done
    
    echo ""
    echo -e "${CYAN}[Press Ctrl+C to quit | Updates every ${REFRESH_INTERVAL}s]${NC}"
}

# Check if AWS CLI is available
if ! command -v aws &> /dev/null; then
    # AWS CLI not found on host, try using docker exec with localstack
    if docker ps | grep -q localstack; then
        USE_DOCKER=true
        echo -e "${YELLOW}Note: Using LocalStack's awslocal via Docker (AWS CLI not found on host)${NC}"
        sleep 2
    else
        echo -e "${RED}Error: AWS CLI is not installed and LocalStack container is not running${NC}"
        echo -e "${YELLOW}Install AWS CLI with: brew install awscli${NC}"
        echo -e "${YELLOW}Or start LocalStack: docker-compose up -d${NC}"
        exit 1
    fi
fi

# Check if LocalStack is running
if ! curl -s "${ENDPOINT_URL}/_localstack/health" > /dev/null 2>&1; then
    echo -e "${RED}Error: Cannot connect to LocalStack at ${ENDPOINT_URL}${NC}"
    echo "Please ensure LocalStack is running: docker-compose up -d"
    exit 1
fi

# Trap Ctrl+C for clean exit
trap 'echo -e "\n${YELLOW}Monitoring stopped.${NC}"; exit 0' INT

# Main loop
echo -e "${GREEN}Starting queue monitor...${NC}"
echo -e "${CYAN}Press Ctrl+C to stop${NC}"
sleep 1

while true; do
    display_dashboard
    sleep "$REFRESH_INTERVAL"
done
