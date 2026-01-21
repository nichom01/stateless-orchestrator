# Quick Start Guide

Get the stateless orchestrator running in 5 minutes!

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- Docker and Docker Compose (for LocalStack)

## Steps

### 1. Start LocalStack

```bash
docker-compose up -d
```

This starts LocalStack with SQS service. Queues will be automatically created when the application starts.

Verify LocalStack is running:
```bash
curl http://localhost:4566/_localstack/health
```

### 2. Build the Project

```bash
mvn clean package
```

### 3. Run the Orchestrator

```bash
java -jar target/stateless-orchestrator-1.0.0.jar
```

Or use Maven:

```bash
mvn spring-boot:run
```

The application will automatically:
- Connect to LocalStack SQS
- Create all required queues from `orchestration-config.yml`
- Start listening for events

### 4. Test It

Submit a test event:

```bash
curl -X POST http://localhost:8080/api/orchestrator/events \
  -H "Content-Type: application/json" \
  -d '{
    "type": "OrderCreated",
    "orchestrationName": "order-processing",
    "correlationId": "test-123",
    "context": {
      "orderId": "ORD-001",
      "customerTier": "premium"
    }
  }'
```

**Note:** The `orchestrationName` field is optional. If omitted, the default orchestration will be used.

Check the logs - you should see:
```
Processing event: type=OrderCreated, eventId=..., correlationId=test-123
Routing event: type=OrderCreated, correlationId=test-123
Event routed successfully: type=OrderCreated, target=validation-service-queue
```

### 5. Test Conditional Routing

```bash
curl -X POST http://localhost:8080/api/orchestrator/events/dry-run \
  -H "Content-Type: application/json" \
  -d '{
    "type": "InventoryReserved",
    "context": {
      "customerTier": "premium",
      "orderTotal": 1500
    }
  }'
```

Response shows where the event would be routed:
```json
{
  "eventType": "InventoryReserved",
  "target": "fraud-check-service-queue",
  "success": true,
  "conditionalRoute": true
}
```

### 6. View Configuration

```bash
curl http://localhost:8080/api/orchestrator/config | jq
```

### 7. Check Health

```bash
curl http://localhost:8080/api/orchestrator/health
```

## Multiple Orchestrations (Optional)

To use multiple orchestration files:

1. **Configure in `application.yml`:**
   ```yaml
   orchestrator:
     config:
       files:
         - name: "order-processing"
           path: "classpath:orchestrations/order-processing.yml"
         - name: "user-registration"
           path: "classpath:orchestrations/user-registration.yml"
       defaultOrchestration: "order-processing"
   ```

2. **List loaded orchestrations:**
   ```bash
   curl http://localhost:8080/api/orchestrator/orchestrations
   ```

3. **Reload a specific orchestration:**
   ```bash
   curl -X POST http://localhost:8080/api/orchestrator/orchestrations/order-processing/reload
   ```

See [MIGRATION_GUIDE.md](MIGRATION_GUIDE.md) for migration from single to multiple orchestrations.

## What's Next?

1. **Customize routing** - Edit `src/main/resources/orchestration-config.yml` or create multiple orchestration files
2. **Add your services** - Create services that consume from the target queues
3. **Hot reload** - Change config and reload without restart:
   ```bash
   curl -X POST http://localhost:8080/api/orchestrator/config/reload
   ```

## Troubleshooting

### Can't connect to LocalStack

Check LocalStack is running:
```bash
docker ps | grep localstack
curl http://localhost:4566/_localstack/health
```

If LocalStack isn't running:
```bash
docker-compose up -d
```

### Queues not created

Queues are automatically created on startup. To verify:
```bash
aws --endpoint-url=http://localhost:4566 sqs list-queues
```

To manually create queues:
```bash
chmod +x scripts/init-localstack.sh
./scripts/init-localstack.sh
```

### Events not routing

1. Check the event type matches exactly (case-sensitive)
2. Check the route is enabled in config
3. Use dry-run to test routing logic
4. Verify queues exist: `aws --endpoint-url=http://localhost:4566 sqs list-queues`

### Need help?

See the full [README.md](README.md) for detailed documentation.
