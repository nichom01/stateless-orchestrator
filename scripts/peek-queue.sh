#!/bin/bash

# Peek at messages in a LocalStack SQS queue without consuming them
# Usage: ./peek-queue.sh <queue-name> [max-messages]

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
    echo -e "${RED}Usage: $0 <queue-name> [max-messages]${NC}"
    echo ""
    echo "Examples:"
    echo "  $0 orchestrator-input"
    echo "  $0 validation-service-queue 5"
    exit 1
fi

QUEUE_NAME=$1
MAX_MESSAGES=${2:-10}

# Validate max messages (SQS limit is 10)
if [ "$MAX_MESSAGES" -gt 10 ]; then
    echo -e "${YELLOW}Warning: SQS max messages is 10, using 10 instead${NC}"
    MAX_MESSAGES=10
fi

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

# Get actual queue URL
QUEUE_URL=$(get_queue_url_from_localstack "${QUEUE_NAME}")

echo -e "${CYAN}Peeking at messages in queue: ${QUEUE_NAME}${NC}"
echo -e "${CYAN}Queue URL: ${QUEUE_URL}${NC}"
echo ""

# Receive messages with visibility timeout 0 (makes them immediately visible again)
if [ "$USE_DOCKER" = true ]; then
    MESSAGES=$(docker exec localstack awslocal sqs receive-message \
        --queue-url "${QUEUE_URL}" \
        --max-number-of-messages "${MAX_MESSAGES}" \
        --visibility-timeout 0 \
        --attribute-names All \
        --output json 2>/dev/null || echo '{"Messages":[]}')
else
    MESSAGES=$(aws --endpoint-url="${ENDPOINT_URL}" \
        --region="${REGION}" \
        sqs receive-message \
        --queue-url "${QUEUE_URL}" \
        --max-number-of-messages "${MAX_MESSAGES}" \
        --visibility-timeout 0 \
        --attribute-names All \
        --output json 2>/dev/null || echo '{"Messages":[]}')
fi

# Check if messages exist
MESSAGE_COUNT=$(echo "$MESSAGES" | grep -o '"MessageId"' | wc -l | tr -d ' ')

if [ "$MESSAGE_COUNT" -eq 0 ]; then
    echo -e "${YELLOW}No messages found in queue${NC}"
    exit 0
fi

echo -e "${GREEN}Found ${MESSAGE_COUNT} message(s):${NC}"
echo ""

# Parse and display messages
echo "$MESSAGES" | python3 -c "
import json
import sys

try:
    data = json.load(sys.stdin)
    messages = data.get('Messages', [])
    
    for i, msg in enumerate(messages, 1):
        print(f'--- Message {i} ---')
        print(f'Message ID: {msg.get(\"MessageId\", \"N/A\")}')
        print(f'Receipt Handle: {msg.get(\"ReceiptHandle\", \"N/A\")[:50]}...')
        
        # Attributes
        attrs = msg.get('Attributes', {})
        if attrs:
            print('Attributes:')
            for key, value in attrs.items():
                print(f'  {key}: {value}')
        
        # Body
        body = msg.get('Body', '')
        print(f'Body:')
        try:
            # Try to parse as JSON for pretty printing
            body_json = json.loads(body)
            print(json.dumps(body_json, indent=2))
        except:
            print(body)
        
        print()
except Exception as e:
    print(f'Error parsing messages: {e}', file=sys.stderr)
    sys.exit(1)
" 2>/dev/null || {
    # Fallback if Python is not available
    echo "$MESSAGES" | grep -A 50 '"Body"' | head -20
    echo ""
    echo -e "${YELLOW}Note: Install Python 3 for better message formatting${NC}"
}
