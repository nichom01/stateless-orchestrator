#!/bin/bash

# LocalStack initialization script
# Verifies LocalStack is ready for use
# Queue creation is handled dynamically by QueueInitializer based on orchestration configs

set -e

ENDPOINT_URL="http://localhost:4566"
REGION="us-east-1"

echo "🚀 Verifying LocalStack SQS service..."

# Wait for LocalStack to be ready
echo "⏳ Waiting for LocalStack to be ready..."
until curl -s "${ENDPOINT_URL}/_localstack/health" | grep -q "\"sqs\": \"available\""; do
  echo "Waiting for SQS service..."
  sleep 2
done

echo "✅ LocalStack is ready!"
echo ""
echo "ℹ️  Queues will be created automatically by QueueInitializer when the application starts."
echo "   Queue names are dynamically extracted from orchestration configs in orchestrations/ directory."
echo ""
echo "Current queues:"
aws --endpoint-url="${ENDPOINT_URL}" --region="${REGION}" sqs list-queues || echo "No queues exist yet (this is expected before app startup)"
