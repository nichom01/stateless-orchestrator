#!/bin/bash

# Purge all messages from a LocalStack SQS queue
# Usage: ./purge-queue.sh <queue-name> [--confirm]

set -e

ENDPOINT_URL="${LOCALSTACK_ENDPOINT:-http://localhost:4566}"
REGION="${AWS_REGION:-us-east-1}"

# Colors
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

# Check arguments
if [ $# -lt 1 ]; then
    echo -e "${RED}Usage: $0 <queue-name> [--confirm]${NC}"
    echo ""
    echo "Examples:"
    echo "  $0 orchestrator-input"
    echo "  $0 validation-service-queue --confirm"
    echo ""
    echo "Note: Use --confirm to skip confirmation prompt"
    exit 1
fi

QUEUE_NAME=$1
SKIP_CONFIRM=${2:-""}

# Function to get actual queue URL
get_queue_url_from_localstack() {
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

# Get actual queue URL
QUEUE_URL=$(get_queue_url_from_localstack "${QUEUE_NAME}")

# Determine which AWS command to use
USE_DOCKER=false

# Check if AWS CLI is available
if ! command -v aws &> /dev/null; then
    # AWS CLI not found on host, try using docker exec with localstack
    if docker ps | grep -q localstack; then
        USE_DOCKER=true
        echo -e "${YELLOW}Note: Using LocalStack's awslocal via Docker (AWS CLI not found on host)${NC}"
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
    exit 1
fi

# Check if queue exists
QUEUE_CHECK_RESULT=1
if [ "$USE_DOCKER" = true ]; then
    docker exec localstack awslocal sqs get-queue-url --queue-name "${QUEUE_NAME}" > /dev/null 2>&1
    QUEUE_CHECK_RESULT=$?
else
    aws --endpoint-url="${ENDPOINT_URL}" --region="${REGION}" sqs get-queue-url --queue-name "${QUEUE_NAME}" > /dev/null 2>&1
    QUEUE_CHECK_RESULT=$?
fi

if [ $QUEUE_CHECK_RESULT -ne 0 ]; then
    echo -e "${RED}Error: Queue '${QUEUE_NAME}' not found${NC}"
    echo ""
    echo "Available queues:"
    if [ "$USE_DOCKER" = true ]; then
        docker exec localstack awslocal sqs list-queues --output table
    else
        aws --endpoint-url="${ENDPOINT_URL}" --region="${REGION}" sqs list-queues --output table
    fi
    exit 1
fi

# Get current message count
if [ "$USE_DOCKER" = true ]; then
    ATTRIBUTES=$(docker exec localstack awslocal sqs get-queue-attributes \
        --queue-url "${QUEUE_URL}" \
        --attribute-names ApproximateNumberOfMessages,ApproximateNumberOfMessagesNotVisible \
        --output json 2>/dev/null || echo '{}')
else
    ATTRIBUTES=$(aws --endpoint-url="${ENDPOINT_URL}" \
        --region="${REGION}" \
        sqs get-queue-attributes \
        --queue-url "${QUEUE_URL}" \
        --attribute-names ApproximateNumberOfMessages,ApproximateNumberOfMessagesNotVisible \
        --output json 2>/dev/null || echo '{}')
fi

AVAILABLE=$(echo "$ATTRIBUTES" | grep -o '"ApproximateNumberOfMessages"[^,}]*' | grep -o '[0-9]*' || echo "0")
IN_FLIGHT=$(echo "$ATTRIBUTES" | grep -o '"ApproximateNumberOfMessagesNotVisible"[^,}]*' | grep -o '[0-9]*' || echo "0")
TOTAL=$((AVAILABLE + IN_FLIGHT))

if [ "$TOTAL" -eq 0 ]; then
    echo -e "${GREEN}Queue '${QUEUE_NAME}' is already empty${NC}"
    exit 0
fi

# Confirmation prompt
if [ "$SKIP_CONFIRM" != "--confirm" ]; then
    echo -e "${YELLOW}Warning: This will delete ALL messages from queue '${QUEUE_NAME}'${NC}"
    echo -e "${CYAN}Current messages: ${TOTAL} (${AVAILABLE} available, ${IN_FLIGHT} in-flight)${NC}"
    echo ""
    read -p "Are you sure you want to purge this queue? (yes/no): " CONFIRM
    
    if [ "$CONFIRM" != "yes" ]; then
        echo -e "${YELLOW}Purge cancelled${NC}"
        exit 0
    fi
fi

# Purge the queue
echo -e "${CYAN}Purging queue '${QUEUE_NAME}'...${NC}"

PURGE_RESULT=1
if [ "$USE_DOCKER" = true ]; then
    docker exec localstack awslocal sqs purge-queue \
        --queue-url "${QUEUE_URL}" \
        --output json 2>&1
    PURGE_RESULT=$?
else
    aws --endpoint-url="${ENDPOINT_URL}" \
        --region="${REGION}" \
        sqs purge-queue \
        --queue-url "${QUEUE_URL}" \
        --output json 2>&1
    PURGE_RESULT=$?
fi

if [ $PURGE_RESULT -eq 0 ]; then
    echo -e "${GREEN}✓ Queue purged successfully${NC}"
    echo -e "${CYAN}Note: It may take up to 60 seconds for the purge to complete${NC}"
else
    echo -e "${RED}Error: Failed to purge queue${NC}"
    exit 1
fi
