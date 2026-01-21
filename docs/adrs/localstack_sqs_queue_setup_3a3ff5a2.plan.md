---
name: LocalStack SQS Queue Setup
overview: Configure LocalStack for local SQS development and implement automated queue creation for all 13 required queues
todos:
  - id: setup-localstack
    content: Set up LocalStack via Docker Compose with SQS service
    status: pending
  - id: create-queue-initializer
    content: Create QueueInitializer component for automatic queue creation on startup
    status: pending
  - id: add-localstack-config
    content: Add LocalStackSqsConfig with endpoint override for dev profile
    status: pending
  - id: update-dev-yml
    content: Update application-dev.yml to use SQS with LocalStack endpoint
    status: pending
  - id: create-init-script
    content: Create init-localstack.sh script for Docker Compose initialization
    status: pending
  - id: update-docs
    content: Update README.md and QUICKSTART.md with LocalStack setup instructions
    status: pending
  - id: test-queue-creation
    content: Test queue creation and message flow with LocalStack
    status: pending
---

# LocalStack SQS Queue Setup Plan

## Overview

This plan covers setting up LocalStack for local SQS development and creating all required queues for the orchestrator application.

## Required Queues (13 Total)

Based on [`orchestration-config.yml`](src/main/resources/orchestration-config.yml):

**Input Queue:**
- `orchestrator-input`

**Target Queues:**
- `validation-service-queue`
- `inventory-service-queue`
- `notification-service-queue`
- `express-payment-service-queue`
- `fraud-check-service-queue`
- `payment-service-queue`
- `order-cancellation-service-queue`
- `inventory-rollback-service-queue`
- `payment-retry-service-queue`
- `fulfillment-service-queue`
- `digital-delivery-service-queue`
- `shipping-service-queue`

## Queue Creation Options

You have **4 main approaches** to choose from:

### Option A: Spring Boot Auto-Configuration (Recommended)
**Best for:** Long-term maintainability and production readiness

- Add a `QueueInitializer` component that creates queues on application startup
- Queues are created automatically when app starts
- Works in both LocalStack (dev) and real AWS (prod)
- No manual steps required
- Idempotent (safe to run multiple times)

**Implementation:**
1. Create `QueueInitializer.java` that reads queue names from orchestration config
2. Uses AWS SDK to create queues if they don't exist
3. Runs on application startup via `@PostConstruct`

### Option B: Docker Compose with Init Script
**Best for:** Complete local dev environment setup

- Single `docker-compose up` starts LocalStack AND creates queues
- Uses init-hooks or awslocal CLI in startup script
- Queues ready before Spring Boot app starts
- Easy for team onboarding

**Implementation:**
1. Create `docker-compose.yml` with LocalStack service
2. Create `init-localstack.sh` script to create queues
3. Mount script as LocalStack init hook
4. Update documentation

### Option C: Standalone Shell Script
**Best for:** Manual control and one-time setup

- Run script once to create all queues
- Simple bash script using AWS CLI
- Works with both LocalStack and real AWS
- Easy to understand and modify

**Implementation:**
1. Create `scripts/create-queues.sh`
2. Uses AWS CLI with LocalStack endpoint
3. Creates all 13 queues in sequence
4. Includes error handling and verification

### Option D: Manual Creation
**Best for:** Learning/exploration (not recommended for team environments)

- Use AWS CLI commands directly
- Create queues one by one
- Good for understanding, but not repeatable

## Recommended Approach

**Primary: Option A (Spring Boot Auto-Configuration)**
- Production-ready
- No manual intervention
- Works across environments
- Team-friendly

**Secondary: Option B (Docker Compose)**
- For complete local environment
- Combines LocalStack + queue setup
- Great developer experience

## Configuration Changes

### Update `application-dev.yml`

```yaml
spring:
  profiles:
    active: dev

# LocalStack SQS Configuration  
orchestrator:
  messaging:
    broker: sqs
    sqs:
      region: us-east-1
      endpoint: http://localhost:4566  # LocalStack endpoint

cloud:
  aws:
    sqs:
      endpoint: http://localhost:4566
    region:
      static: us-east-1
    credentials:
      access-key: test
      secret-key: test
```

### Add LocalStack SQS Config Bean

Create configuration for LocalStack endpoint override in dev profile.

## LocalStack Setup

### Docker Command

```bash
docker run -d \
  --name localstack \
  -p 4566:4566 \
  -p 4571:4571 \
  -e SERVICES=sqs \
  -e DEBUG=1 \
  localstack/localstack:latest
```

### Docker Compose (Recommended)

```yaml
version: '3.8'
services:
  localstack:
    image: localstack/localstack:latest
    ports:
      - "4566:4566"
    environment:
      - SERVICES=sqs
      - DEBUG=1
      - DEFAULT_REGION=us-east-1
    volumes:
      - ./scripts/init-localstack.sh:/etc/localstack/init/ready.d/init-localstack.sh
```

## Implementation Files

### Files to Create/Modify:

1. **`src/main/java/com/example/orchestrator/config/QueueInitializer.java`** (Option A)
   - Reads queue names from OrchestrationConfig
   - Creates queues on startup if they don't exist

2. **`docker-compose.yml`** (Option B)
   - LocalStack service definition
   - Volume mounts for init scripts

3. **`scripts/init-localstack.sh`** (Options B & C)
   - Bash script to create all queues
   - Uses awslocal CLI

4. **`src/main/java/com/example/orchestrator/config/LocalStackSqsConfig.java`**
   - Dev profile configuration
   - LocalStack endpoint override

5. **`application-dev.yml`**
   - Update to use SQS instead of RabbitMQ
   - Add LocalStack endpoint configuration

6. **`README.md` and `QUICKSTART.md`**
   - Update with LocalStack instructions
   - Document queue creation process

## Testing & Verification

### Verify LocalStack is Running

```bash
curl http://localhost:4566/_localstack/health
```

### List Created Queues

```bash
aws --endpoint-url=http://localhost:4566 sqs list-queues
```

### Test Message Flow

```bash
# Send test event
curl -X POST http://localhost:8080/api/orchestrator/events \
  -H "Content-Type: application/json" \
  -d '{"type": "OrderCreated", "correlationId": "test-001", "context": {"orderId": "ORD-001"}}'

# Check queue
aws --endpoint-url=http://localhost:4566 sqs receive-message \
  --queue-url http://localhost:4566/000000000000/validation-service-queue
```

## Migration Strategy

### Phase 1: Add LocalStack Support (Keep RabbitMQ as fallback)
- Add LocalStack configuration
- Implement queue creation
- Test locally

### Phase 2: Update Dev Profile
- Switch dev profile from RabbitMQ to SQS
- Update documentation
- Team testing

### Phase 3: Cleanup (Optional)
- Remove RabbitMQ dependencies if not needed
- Simplify configuration

## Next Steps

1. Choose your preferred queue creation option (A, B, C, or D)
2. I'll implement the selected approach
3. Update configuration for LocalStack
4. Test the complete flow
