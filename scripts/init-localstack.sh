#!/bin/bash

# LocalStack initialization script
# Creates all required SQS queues when LocalStack starts
# This script runs automatically when LocalStack is ready

set -e

ENDPOINT_URL="http://localhost:4566"
REGION="us-east-1"

echo "🚀 Initializing LocalStack SQS queues..."

# Wait for LocalStack to be ready
echo "⏳ Waiting for LocalStack to be ready..."
until curl -s "${ENDPOINT_URL}/_localstack/health" | grep -q "\"sqs\": \"available\""; do
  echo "Waiting for SQS service..."
  sleep 2
done

echo "✅ LocalStack is ready!"

# Function to create a queue if it doesn't exist
create_queue() {
    local queue_name=$1
    echo "Creating queue: ${queue_name}"
    
    aws --endpoint-url="${ENDPOINT_URL}" \
        --region="${REGION}" \
        sqs create-queue \
        --queue-name "${queue_name}" \
        --attributes '{"VisibilityTimeout": "30", "MessageRetentionPeriod": "345600"}' \
        2>&1 | grep -v "already exists" || true
    
    echo "✅ Queue created/verified: ${queue_name}"
}

# Input queue
create_queue "orchestrator-input"

# Target queues from orchestration-config.yml
create_queue "validation-service-queue"
create_queue "inventory-service-queue"
create_queue "notification-service-queue"
create_queue "express-payment-service-queue"
create_queue "fraud-check-service-queue"
create_queue "payment-service-queue"
create_queue "order-cancellation-service-queue"
create_queue "inventory-rollback-service-queue"
create_queue "payment-retry-service-queue"
create_queue "fulfillment-service-queue"
create_queue "digital-delivery-service-queue"
create_queue "shipping-service-queue"

echo ""
echo "🎉 All queues initialized successfully!"
echo ""
echo "List of created queues:"
aws --endpoint-url="${ENDPOINT_URL}" --region="${REGION}" sqs list-queues
