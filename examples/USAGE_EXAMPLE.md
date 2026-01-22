# Example Usage Guide

## Scenario: E-commerce Order Processing

This example demonstrates a complete order processing workflow using the stateless orchestrator with AWS SQS (LocalStack for local development).

### 1. Start LocalStack

Start LocalStack with SQS support using docker-compose:

```bash
docker-compose up -d
```

Verify LocalStack is running:

```bash
# Check container status
docker ps | grep localstack

# Check health
curl http://localhost:4566/_localstack/health
```

### 2. Start the Orchestrator

Start the application with the `dev` profile to use LocalStack:

```bash
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
```

You should see logs confirming:
- ✅ Configuring SQS client for LocalStack at endpoint: http://localhost:4566
- ✅ Queue initialization complete: 13 created

### 3. Submit Test Events

#### Create an Order (Standard Customer)

```bash
curl -X POST http://localhost:8080/api/orchestrator/events \
  -H "Content-Type: application/json" \
  -d '{
    "type": "OrderCreated",
    "correlationId": "order-001",
    "context": {
      "orderId": "ORD-001",
      "customerId": "CUST-123",
      "customerTier": "standard",
      "orderTotal": 99.99,
      "items": [
        {"sku": "ITEM-001", "quantity": 2, "price": 49.99}
      ]
    }
  }'
```

**Expected routing:** `OrderCreated` → `validation-service-queue`

#### Bulk Load Test (2500 Orders)

For load testing and performance evaluation, submit 2500 OrderCreated events using the bulk upload API endpoint:

**Using NDJSON format (recommended for file uploads):**

```bash
# Submit the entire test file in one API call
curl -X POST http://localhost:8080/api/orchestrator/events/bulk-ndjson \
  -H "Content-Type: text/plain" \
  --data-binary @examples/test-orders-2500.jsonl \
  | jq
```

**Response example:**
```json
{
  "total": 2500,
  "successful": 2500,
  "failed": 0,
  "failures": [],
  "durationMs": 1234
}
```

**Using JSON array format:**

```bash
# Convert NDJSON to JSON array and submit
cat examples/test-orders-2500.jsonl | jq -s '.' | \
  curl -X POST http://localhost:8080/api/orchestrator/events/bulk \
  -H "Content-Type: application/json" \
  -d @- | jq
```

**Using the wrapper format:**

```bash
# Wrap events in a BulkEventRequest object
cat examples/test-orders-2500.jsonl | jq -s '{events: .}' | \
  curl -X POST http://localhost:8080/api/orchestrator/events/bulk \
  -H "Content-Type: application/json" \
  -d @- | jq
```

**Monitor progress:**

While submitting events, monitor the queue in another terminal:

```bash
# In a separate terminal, watch the queue stats
watch -n 1 ./scripts/queue-stats.sh

# Or use the real-time dashboard
./scripts/monitor-queues.sh
```

**Expected behavior:**
- All 2500 events should route to `validation-service-queue`
- Monitor queue depth to see messages accumulating
- Check application logs for any errors
- Verify all events are processed successfully
- The API returns statistics about successful/failed events

**Note:** The test file (`examples/test-orders-2500.jsonl`) contains NDJSON format (one JSON object per line) with varied order data including different customer tiers, order totals, and item quantities.

**Legacy script-based approach (for reference):**

If you prefer the old script-based approach for comparison or testing:

```bash
# Using the helper script
./examples/submit-bulk-orders.sh examples/test-orders-2500.jsonl http://localhost:8080/api/orchestrator/events 20
```

#### Validation Success

```bash
curl -X POST http://localhost:8080/api/orchestrator/events \
  -H "Content-Type: application/json" \
  -d '{
    "type": "OrderValidated",
    "correlationId": "order-001",
    "context": {
      "orderId": "ORD-001",
      "customerId": "CUST-123",
      "customerTier": "standard",
      "orderTotal": 99.99,
      "validated": true
    }
  }'
```

**Expected routing:** `OrderValidated` → `inventory-service-queue`

#### Inventory Reserved (Standard Customer)

```bash
curl -X POST http://localhost:8080/api/orchestrator/events \
  -H "Content-Type: application/json" \
  -d '{
    "type": "InventoryReserved",
    "correlationId": "order-001",
    "context": {
      "orderId": "ORD-001",
      "customerId": "CUST-123",
      "customerTier": "standard",
      "orderTotal": 99.99,
      "inventoryReserved": true,
      "reservationId": "RES-001"
    }
  }'
```

**Expected routing:** `InventoryReserved` → `payment-service-queue` (standard tier, low value)

#### Payment Success

```bash
curl -X POST http://localhost:8080/api/orchestrator/events \
  -H "Content-Type: application/json" \
  -d '{
    "type": "PaymentSucceeded",
    "correlationId": "order-001",
    "context": {
      "orderId": "ORD-001",
      "customerId": "CUST-123",
      "orderTotal": 99.99,
      "paymentId": "PAY-001",
      "inventoryReserved": true
    }
  }'
```

**Expected routing:** `PaymentSucceeded` → `fulfillment-service-queue`

---

### 4. Test Conditional Routing

#### Premium Customer Order

```bash
curl -X POST http://localhost:8080/api/orchestrator/events \
  -H "Content-Type: application/json" \
  -d '{
    "type": "InventoryReserved",
    "correlationId": "order-002",
    "context": {
      "orderId": "ORD-002",
      "customerId": "CUST-789",
      "customerTier": "premium",
      "orderTotal": 299.99,
      "inventoryReserved": true
    }
  }'
```

**Expected routing:** `InventoryReserved` → `express-payment-service-queue` (premium tier)

#### High-Value Order (Fraud Check)

```bash
curl -X POST http://localhost:8080/api/orchestrator/events \
  -H "Content-Type: application/json" \
  -d '{
    "type": "InventoryReserved",
    "correlationId": "order-003",
    "context": {
      "orderId": "ORD-003",
      "customerId": "CUST-456",
      "customerTier": "standard",
      "orderTotal": 2500.00,
      "inventoryReserved": true
    }
  }'
```

**Expected routing:** `InventoryReserved` → `fraud-check-service-queue` (high value)

---

### 5. Test Error Handling

#### Payment Failure with Inventory Rollback

```bash
curl -X POST http://localhost:8080/api/orchestrator/events \
  -H "Content-Type: application/json" \
  -d '{
    "type": "PaymentFailed",
    "correlationId": "order-004",
    "context": {
      "orderId": "ORD-004",
      "customerId": "CUST-111",
      "orderTotal": 150.00,
      "inventoryReserved": true,
      "reservationId": "RES-004",
      "failureReason": "Insufficient funds"
    }
  }'
```

**Expected routing:** `PaymentFailed` → `inventory-rollback-service-queue` (inventory was reserved)

#### Payment Failure with Retry

```bash
curl -X POST http://localhost:8080/api/orchestrator/events \
  -H "Content-Type: application/json" \
  -d '{
    "type": "PaymentFailed",
    "correlationId": "order-005",
    "context": {
      "orderId": "ORD-005",
      "customerId": "CUST-222",
      "orderTotal": 75.00,
      "inventoryReserved": false,
      "retryCount": 1,
      "failureReason": "Gateway timeout"
    }
  }'
```

**Expected routing:** `PaymentFailed` → `payment-retry-service-queue` (retry count < 3)

---

### 6. Test Routing (Dry Run)

Test routing decisions without actually dispatching events:

```bash
curl -X POST http://localhost:8080/api/orchestrator/events/dry-run \
  -H "Content-Type: application/json" \
  -d '{
    "type": "InventoryReserved",
    "context": {
      "customerTier": "premium",
      "orderTotal": 500.00
    }
  }'
```

Response:
```json
{
  "eventType": "InventoryReserved",
  "target": "express-payment-service-queue",
  "success": true,
  "conditionalRoute": true,
  "errorMessage": null
}
```

---

### 7. View Current Configuration

```bash
curl http://localhost:8080/api/orchestrator/config | jq
```

### 8. Check Health

```bash
curl http://localhost:8080/api/orchestrator/health | jq
```

### 9. View Metrics

```bash
curl http://localhost:8080/actuator/prometheus
```

---

## Complete Order Flow Example

Here's a complete successful order flow:

```bash
# 1. Order created
curl -X POST http://localhost:8080/api/orchestrator/events \
  -H "Content-Type: application/json" \
  -d '{"type": "OrderCreated", "correlationId": "order-complete-001", "context": {"orderId": "ORD-999", "customerTier": "standard", "orderTotal": 150.00}}'

# 2. Order validated
curl -X POST http://localhost:8080/api/orchestrator/events \
  -H "Content-Type: application/json" \
  -d '{"type": "OrderValidated", "correlationId": "order-complete-001", "context": {"orderId": "ORD-999", "customerTier": "standard", "orderTotal": 150.00}}'

# 3. Inventory reserved
curl -X POST http://localhost:8080/api/orchestrator/events \
  -H "Content-Type: application/json" \
  -d '{"type": "InventoryReserved", "correlationId": "order-complete-001", "context": {"orderId": "ORD-999", "customerTier": "standard", "orderTotal": 150.00, "inventoryReserved": true}}'

# 4. Payment succeeded
curl -X POST http://localhost:8080/api/orchestrator/events \
  -H "Content-Type: application/json" \
  -d '{"type": "PaymentSucceeded", "correlationId": "order-complete-001", "context": {"orderId": "ORD-999", "paymentId": "PAY-999"}}'

# 5. Fulfillment created
curl -X POST http://localhost:8080/api/orchestrator/events \
  -H "Content-Type: application/json" \
  -d '{"type": "FulfillmentCreated", "correlationId": "order-complete-001", "context": {"orderId": "ORD-999", "isDigital": false}}'
```

Each step routes to the next service in the workflow!

---

## Monitoring the Flow

### Watch Application Logs

Watch the logs to see routing decisions:

```bash
tail -f logs/spring.log | grep "Routing event"
```

Or in real-time during `mvn spring-boot:run`:
```
2026-01-20 23:53:13 - Routing event: type=OrderCreated, correlationId=order-001
2026-01-20 23:53:13 - Using default route: OrderCreated -> validation-service-queue
```

### Check SQS Queues in LocalStack

List all queues:

```bash
# Using awslocal (if installed)
docker exec localstack awslocal sqs list-queues

# Using AWS CLI with LocalStack endpoint
aws --endpoint-url=http://localhost:4566 sqs list-queues --region us-east-1
```

Check queue attributes:

```bash
docker exec localstack awslocal sqs get-queue-attributes \
  --queue-url http://sqs.us-east-1.localhost.localstack.cloud:4566/000000000000/validation-service-queue \
  --attribute-names ApproximateNumberOfMessages
```

Receive messages from a queue:

```bash
docker exec localstack awslocal sqs receive-message \
  --queue-url http://sqs.us-east-1.localhost.localstack.cloud:4566/000000000000/validation-service-queue
```

### Use Queue Stats Script

The project includes a helpful queue stats script:

```bash
./scripts/queue-stats.sh
```

This will show message counts for all queues.

### LocalStack Web UI (Pro Feature)

If you have LocalStack Pro, you can access the web UI at:
https://app.localstack.cloud

For the free version, use the AWS CLI commands shown above.

---

## Production Deployment (Real AWS SQS)

For production deployment with real AWS SQS:

### 1. Set AWS Credentials

```bash
export AWS_REGION=us-east-1
export AWS_ACCESS_KEY_ID=your-access-key
export AWS_SECRET_ACCESS_KEY=your-secret-key
```

Or use IAM roles (recommended for EC2/ECS/EKS):

```bash
# IAM role is automatically detected - no credentials needed
```

### 2. Run with Production Profile

```bash
SPRING_PROFILES_ACTIVE=prod java -jar target/stateless-orchestrator-1.0.0.jar
```

### 3. Verify Production Setup

```bash
# List queues in real AWS
aws sqs list-queues --region us-east-1

# Check application health
curl http://your-production-host:8080/actuator/health

# View metrics
curl http://your-production-host:8080/actuator/prometheus
```

### Key Differences: Dev vs Prod

| Feature | Dev Profile (LocalStack) | Prod Profile (AWS) |
|---------|-------------------------|-------------------|
| **Endpoint** | `http://localhost:4566` | AWS SQS endpoint |
| **Credentials** | Dummy (`test/test`) | Real AWS credentials |
| **Queue URLs** | `http://sqs.us-east-1.localhost.localstack.cloud:4566/...` | `https://sqs.us-east-1.amazonaws.com/...` |
| **Cost** | Free | AWS charges apply |
| **Data Persistence** | Optional (volume-based) | Always persisted |
| **Network** | Local only | Internet/VPC |

### Environment Variables

Both profiles support these environment variables:

```bash
# Messaging broker type (currently only 'sqs' is supported)
MESSAGING_BROKER=sqs

# AWS region
AWS_REGION=us-east-1

# For dev profile only
LOCALSTACK_ENDPOINT=http://localhost:4566
```

---

## Troubleshooting

### LocalStack not starting

```bash
# Clean restart
docker-compose down -v
docker-compose up -d

# Check logs
docker logs localstack
```

### Application can't connect to LocalStack

```bash
# Verify LocalStack is running
docker ps | grep localstack

# Test connectivity
curl http://localhost:4566/_localstack/health

# Ensure dev profile is active
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
```

### Queues not being created

Check the orchestration configuration files:
```bash
cat src/main/resources/orchestrations/order-processing.yml
cat src/main/resources/orchestrations/user-registration.yml
```

Ensure all queue names are valid and the application has proper AWS/LocalStack permissions.

### Messages not being processed

```bash
# Check if listener container is running
curl http://localhost:8080/actuator/health | jq

# Verify messages are in the queue
docker exec localstack awslocal sqs get-queue-attributes \
  --queue-url http://sqs.us-east-1.localhost.localstack.cloud:4566/000000000000/orchestrator-input \
  --attribute-names ApproximateNumberOfMessages
```
