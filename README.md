# Stateless Orchestrator

A configurable, production-ready stateless orchestrator for event-driven microservices. Routes events based on declarative YAML/JSON configuration without maintaining workflow state.

## Table of Contents

- [Overview](#overview)
- [Key Features](#key-features)
- [Architecture](#architecture)
- [Configuration File Structure](#configuration-file-structure)
- [Getting Started](#getting-started)
- [API Reference](#api-reference)
- [Examples](#examples)
- [Deployment](#deployment)
- [Monitoring](#monitoring)

## Overview

This orchestrator implements the **stateless orchestration pattern** where:
- All workflow context travels with events
- Routing logic is centralized in configuration
- No workflow state is maintained between events
- Horizontally scalable (multiple instances can run concurrently)
- Perfect for serverless and container environments

### Why Stateless Orchestration?

**Benefits:**
- ✅ Horizontally scalable - No state synchronization needed
- ✅ Simple deployment - No database for workflow state
- ✅ Cost-effective - Only runs when processing events
- ✅ Easy to change - Update routing without touching services
- ✅ Clear visibility - All routing logic in one place
- ✅ Testable - Pure routing functions

**Best for:**
- E-commerce order processing
- Financial transaction workflows
- Document processing pipelines
- API orchestration
- Event-driven microservices

## Key Features

### 1. Declarative Configuration

Define routing rules in YAML or JSON:

```yaml
routes:
  - eventType: "OrderCreated"
    defaultTarget: "validation-service-queue"
  
  - eventType: "PaymentFailed"
    conditions:
      - condition: "#context['inventoryReserved'] == true"
        target: "inventory-rollback-service-queue"
    defaultTarget: "notification-service-queue"
```

### 2. Conditional Routing

Use Spring Expression Language (SpEL) for complex routing logic:

```yaml
conditions:
  - condition: "#context['customerTier'] == 'premium'"
    target: "express-payment-service-queue"
  
  - condition: "#context['orderTotal'] > 1000"
    target: "fraud-check-service-queue"
```

### 3. Hot Reload

Reload configuration without restarting:

```bash
curl -X POST http://localhost:8080/api/orchestrator/config/reload
```

### 4. Built-in Observability

- Prometheus metrics
- Audit logging
- Health checks
- Distributed tracing ready

### 5. Multiple Queue Backends

Supports:
- **AWS SQS with LocalStack** (local development - recommended)
- **AWS SQS** (production)
- RabbitMQ (legacy local development option)
- Apache Kafka (extensible)

Switch between brokers using Spring profiles - no code changes needed!

### 6. Multiple Orchestrations

Support multiple independent orchestration workflows, each with its own configuration file:

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

Events specify which orchestration to use:

```json
{
  "type": "OrderCreated",
  "orchestrationName": "order-processing",
  "correlationId": "order-12345",
  "context": { "orderId": "ORD-001" }
}
```

**Benefits:**
- ✅ Separate workflows into independent files
- ✅ Different teams can own different orchestrations
- ✅ Selective reloading of individual orchestrations
- ✅ Better organization and maintainability

See [Multiple Orchestrations](#multiple-orchestrations) section for details.

## Multi-Broker Support

The orchestrator supports multiple message brokers through a clean abstraction layer. **LocalStack** is recommended for local development to ensure dev-prod parity with AWS SQS.

### Broker Selection

**Local Development (LocalStack SQS - Recommended):**
```bash
# Start LocalStack
docker-compose up -d

# Run application (uses dev profile with LocalStack SQS)
mvn spring-boot:run

# Or explicitly set profile
mvn spring-boot:run -Dspring.profiles.active=dev
```

**Production (AWS SQS):**
```bash
# Set prod profile for SQS
mvn spring-boot:run -Dspring.profiles.active=prod

# Or via environment variable
export SPRING_PROFILES_ACTIVE=prod
java -jar target/stateless-orchestrator-1.0.0.jar
```

### LocalStack Setup (Local Development)

**Prerequisites:**
- Docker and Docker Compose installed
- AWS CLI installed (optional, for manual queue operations)

**Quick Start:**

1. **Start LocalStack:**
```bash
docker-compose up -d
```

2. **Verify LocalStack is running:**
```bash
curl http://localhost:4566/_localstack/health
```

3. **Queues are automatically created** when the application starts via `QueueInitializer`, or you can use the init script:
```bash
# Make script executable
chmod +x scripts/init-localstack.sh

# Run manually if needed
./scripts/init-localstack.sh
```

4. **List created queues:**
```bash
aws --endpoint-url=http://localhost:4566 sqs list-queues
```

**Configuration (application-dev.yml):**
```yaml
orchestrator:
  messaging:
    broker: sqs
    sqs:
      region: us-east-1
      endpoint: http://localhost:4566
      queueUrlPrefix: http://localhost:4566/000000000000/
```

**Monitoring LocalStack Queues:**

The project includes several scripts to monitor and manage SQS queues in LocalStack:

1. **Real-time Queue Dashboard:**
   ```bash
   # Monitor all queues with auto-refresh (updates every 2 seconds)
   ./scripts/monitor-queues.sh
   
   # Customize refresh interval
   REFRESH_INTERVAL=5 ./scripts/monitor-queues.sh
   ```
   Displays a live dashboard showing:
   - Messages available (ready to process)
   - Messages in-flight (being processed)
   - Delayed messages
   - Age of oldest message
   - Queue status (Idle/Active)

2. **One-time Queue Statistics:**
   ```bash
   # Table format (default)
   ./scripts/queue-stats.sh
   
   # JSON format
   ./scripts/queue-stats.sh --json
   ```
   Shows a snapshot of all queue metrics without auto-refresh.

3. **Peek at Queue Messages:**
   ```bash
   # View messages without consuming them
   ./scripts/peek-queue.sh orchestrator-input
   
   # Limit number of messages (max 10)
   ./scripts/peek-queue.sh validation-service-queue 5
   ```
   Useful for debugging - shows message content without removing messages from the queue.

4. **Purge a Queue:**
   ```bash
   # Clear all messages from a queue (with confirmation)
   ./scripts/purge-queue.sh orchestrator-input
   
   # Skip confirmation prompt
   ./scripts/purge-queue.sh orchestrator-input --confirm
   ```
   Removes all messages from a queue (useful for testing).

**Manual AWS CLI Commands:**

You can also use AWS CLI directly with LocalStack:

```bash
# List all queues
aws --endpoint-url=http://localhost:4566 --region=us-east-1 sqs list-queues

# Get queue attributes
aws --endpoint-url=http://localhost:4566 --region=us-east-1 sqs get-queue-attributes \
  --queue-url http://localhost:4566/000000000000/orchestrator-input \
  --attribute-names All

# Receive messages (peek)
aws --endpoint-url=http://localhost:4566 --region=us-east-1 sqs receive-message \
  --queue-url http://localhost:4566/000000000000/orchestrator-input \
  --max-number-of-messages 10 \
  --visibility-timeout 0
```

### AWS SQS Setup (Production)

1. **Create SQS Queues** in AWS Console or via IaC (Terraform, CloudFormation)

2. **Configure AWS Credentials** (one of):
   - Environment variables: `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`
   - IAM role (for EC2/ECS/Lambda)
   - AWS credentials file: `~/.aws/credentials`
   - Default credentials provider chain (recommended for production)

3. **Set Environment Variables:**
```bash
export SPRING_PROFILES_ACTIVE=prod
export AWS_REGION=us-east-1
export SQS_QUEUE_URL_PREFIX=https://sqs.us-east-1.amazonaws.com/123456789012/
```

4. **Queue Naming:**
   - If `queueUrlPrefix` is set: Use simple queue names (e.g., `validation-service-queue`)
   - If `queueUrlPrefix` is not set: Use full queue URLs in config

5. **Automatic Queue Creation:**
   - Queues are automatically created on application startup via `QueueInitializer`
   - The initializer reads queue names from orchestration configs in `orchestrations/` directory
   - Idempotent - safe to run multiple times

### Architecture

The messaging abstraction uses the Strategy pattern:

```
EventDispatcher → MessageBroker (interface)
                      ↓
        ┌─────────────┴─────────────┐
        ↓                           ↓
RabbitMQMessageBroker      SQSMessageBroker
(@Profile("dev"))          (@Profile("prod"))
```

## Architecture

```
┌─────────────┐
│   Service   │
│     A       │
└──────┬──────┘
       │ Emits Event
       ↓
┌─────────────────────────────┐
│   Orchestrator Input Queue  │
└─────────────┬───────────────┘
              │
              ↓
┌─────────────────────────────┐
│  Stateless Orchestrator     │
│  ┌────────────────────────┐ │
│  │  1. Receive Event      │ │
│  │  2. Load Config        │ │
│  │  3. Evaluate Routing   │ │
│  │  4. Dispatch to Target │ │
│  └────────────────────────┘ │
└─────────────┬───────────────┘
              │
       ┌──────┴───────┐
       ↓              ↓
┌────────────┐  ┌────────────┐
│  Service   │  │  Service   │
│     B      │  │     C      │
└────────────┘  └────────────┘
```

## Configuration File Structure

### Complete Example

```yaml
# Metadata
name: "Order Processing Orchestration"
version: "1.0.0"
description: "Routes events for order processing workflow"

# Global Settings
settings:
  queuePrefix: "order-processing"      # Prefix for all queues
  auditEnabled: true                   # Enable audit logging
  metricsEnabled: true                 # Enable metrics collection
  defaultTimeoutMs: 30000              # Default operation timeout

# Routing Rules
routes:
  - eventType: "OrderCreated"          # Event type to match
    description: "New order submitted"  # Human-readable description
    defaultTarget: "validation-queue"   # Where to route by default
    enabled: true                       # Enable/disable this route
    tags:                               # Tags for organization
      - "order"
      - "entry-point"
  
  - eventType: "InventoryReserved"
    description: "Inventory reserved"
    
    # Conditional routing (evaluated in priority order)
    conditions:
      - condition: "#context['customerTier'] == 'premium'"
        target: "express-payment-queue"
        description: "Premium customers"
        priority: 1                     # Lower = higher priority
      
      - condition: "#context['orderTotal'] > 1000"
        target: "fraud-check-queue"
        description: "High-value orders"
        priority: 2
    
    defaultTarget: "payment-queue"      # Used if no conditions match
    enabled: true
```

### Configuration Fields

#### Top Level

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | string | Yes | Name of this orchestration configuration |
| `version` | string | No | Version identifier |
| `description` | string | No | Human-readable description |
| `settings` | object | No | Global orchestrator settings |
| `routes` | array | Yes | List of routing definitions |

#### Settings

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `queuePrefix` | string | `""` | Prefix added to all queue names |
| `auditEnabled` | boolean | `true` | Enable audit logging |
| `metricsEnabled` | boolean | `true` | Enable metrics collection |
| `defaultTimeoutMs` | number | `30000` | Default timeout in milliseconds |

#### Route Definition

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `eventType` | string | Yes | Event type this route handles (e.g., "OrderCreated") |
| `description` | string | No | Human-readable description |
| `conditions` | array | No | Conditional routing rules |
| `defaultTarget` | string | Conditional | Default target if no conditions match |
| `enabled` | boolean | No (default: true) | Whether this route is active |
| `tags` | array | No | Tags for organization/filtering |

#### Conditional Route

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `condition` | string | Yes | SpEL expression to evaluate |
| `target` | string | Yes | Target queue if condition is true |
| `description` | string | No | Description of this condition |
| `priority` | number | No (default: 0) | Priority (lower = higher priority) |

### SpEL Expression Reference

Access event data in conditions using Spring Expression Language:

| Expression | Description | Example |
|------------|-------------|---------|
| `#context['key']` | Access context value | `#context['customerTier'] == 'premium'` |
| `#context['key'] == value` | Equality check | `#context['status'] == 'approved'` |
| `#context['key'] > value` | Comparison | `#context['orderTotal'] > 1000` |
| `#context['key'] != null` | Null check | `#context['paymentId'] != null` |
| `#context['key'] && #context['key2']` | Logical AND | `#context['paid'] && #context['shipped']` |
| `#type` | Access event type | `#type == 'OrderCreated'` |
| `#correlationId` | Access correlation ID | `#correlationId != null` |

### Common Patterns

#### 1. Simple Linear Flow

```yaml
routes:
  - eventType: "OrderCreated"
    defaultTarget: "validation-queue"
  
  - eventType: "OrderValidated"
    defaultTarget: "inventory-queue"
  
  - eventType: "InventoryReserved"
    defaultTarget: "payment-queue"
```

#### 2. Customer Tier Routing

```yaml
routes:
  - eventType: "OrderValidated"
    conditions:
      - condition: "#context['customerTier'] == 'enterprise'"
        target: "enterprise-processing-queue"
        priority: 1
      
      - condition: "#context['customerTier'] == 'premium'"
        target: "premium-processing-queue"
        priority: 2
    
    defaultTarget: "standard-processing-queue"
```

#### 3. Error Handling with Rollback

```yaml
routes:
  - eventType: "PaymentFailed"
    conditions:
      - condition: "#context['inventoryReserved'] == true"
        target: "inventory-rollback-queue"
        description: "Rollback inventory if reserved"
    
    defaultTarget: "notification-queue"
```

#### 4. Retry Logic

```yaml
routes:
  - eventType: "PaymentFailed"
    conditions:
      - condition: "#context['retryCount'] < 3"
        target: "payment-retry-queue"
        description: "Retry if under limit"
    
    defaultTarget: "payment-failure-queue"
```

#### 5. A/B Testing

```yaml
routes:
  - eventType: "OrderCreated"
    conditions:
      - condition: "#context['experimentGroup'] == 'B'"
        target: "new-validation-queue"
        description: "Experimental validation service"
    
    defaultTarget: "validation-queue"
```

## Getting Started

### Prerequisites

- Java 17+
- Maven 3.6+
- Docker and Docker Compose (for LocalStack local development)
- AWS account (for production with SQS)

### Installation

1. **Clone or download the project**

2. **Start LocalStack** (for local development)

```bash
docker-compose up -d
```

This starts LocalStack with SQS service. Queues will be automatically created when the application starts.

3. **Customize orchestration configuration**

Edit orchestration files in `src/main/resources/orchestrations/` directory (e.g., `order-processing.yml`, `user-registration.yml`) with your routing rules.

4. **Build the project**

```bash
mvn clean package
```

5. **Run the application**

**Local Development (LocalStack SQS - default):**
```bash
# Uses dev profile automatically with LocalStack
mvn spring-boot:run

# Or explicitly
mvn spring-boot:run -Dspring.profiles.active=dev
```

**Production (AWS SQS):**
```bash
# Set prod profile
export SPRING_PROFILES_ACTIVE=prod
export AWS_REGION=us-east-1
java -jar target/stateless-orchestrator-1.0.0.jar
```

### Quick Test

1. **Start the orchestrator**

```bash
mvn spring-boot:run
```

2. **Submit a test event**

```bash
curl -X POST http://localhost:8080/api/orchestrator/events \
  -H "Content-Type: application/json" \
  -d '{
    "type": "OrderCreated",
    "correlationId": "test-123",
    "context": {
      "orderId": "ORD-001",
      "customerId": "CUST-456",
      "customerTier": "premium",
      "orderTotal": 150.00
    }
  }'
```

3. **Check routing (dry run)**

```bash
curl -X POST http://localhost:8080/api/orchestrator/events/dry-run \
  -H "Content-Type: application/json" \
  -d '{
    "type": "InventoryReserved",
    "context": {
      "customerTier": "premium",
      "orderTotal": 1500.00
    }
  }'
```

Response:
```json
{
  "eventType": "InventoryReserved",
  "target": "fraud-check-service-queue",
  "success": true,
  "conditionalRoute": true,
  "errorMessage": null
}
```

## API Reference

### Submit Event

Submit an event for processing.

**Endpoint:** `POST /api/orchestrator/events`

**Request Body:**
```json
{
  "type": "OrderCreated",
  "orchestrationName": "order-processing",
  "correlationId": "order-12345",
  "context": {
    "orderId": "ORD-001",
    "customerId": "CUST-456",
    "orderTotal": 99.99
  }
}
```

**Note:** The `orchestrationName` field is optional. If not specified, the default orchestration will be used.

**Response:**
```json
{
  "eventId": "evt-uuid",
  "status": "ACCEPTED",
  "correlationId": "order-12345"
}
```

### Dry Run (Test Routing)

Test routing without dispatching the event.

**Endpoint:** `POST /api/orchestrator/events/dry-run`

**Request Body:** Same as submit event

**Response:**
```json
{
  "eventType": "OrderCreated",
  "target": "validation-service-queue",
  "success": true,
  "conditionalRoute": false
}
```

### Get Configuration

Retrieve current orchestration configuration.

**Endpoint:** `GET /api/orchestrator/config`

**Response:** Full orchestration config as JSON

### Reload Configuration

Reload configuration from file without restart.

**Endpoint:** `POST /api/orchestrator/config/reload`

**Response:**
```json
{
  "status": "RELOADED",
  "configName": "Order Processing Orchestration",
  "version": "1.0.0",
  "routeCount": 15
}
```

### Health Check

Check orchestrator health.

**Endpoint:** `GET /api/orchestrator/health`

**Response:**
```json
{
  "status": "UP",
  "configValid": true,
  "configName": "Order Processing Orchestration"
}
```

### List Orchestrations

List all loaded orchestration configurations.

**Endpoint:** `GET /api/orchestrator/orchestrations`

**Response:**
```json
{
  "orchestrations": ["order-processing", "user-registration"],
  "count": 2
}
```

### Get Orchestration Configuration

Retrieve a specific orchestration configuration.

**Endpoint:** `GET /api/orchestrator/orchestrations/{name}`

**Response:** Full orchestration config as JSON

### Reload Orchestration

Reload a specific orchestration configuration without restarting.

**Endpoint:** `POST /api/orchestrator/orchestrations/{name}/reload`

**Response:**
```json
{
  "status": "RELOADED",
  "orchestration": "order-processing",
  "version": "1.0.0",
  "routeCount": 5
}
```

## Multiple Orchestrations

The orchestrator supports multiple independent orchestration workflows, each with its own configuration file. This allows you to:

- Separate different business workflows into independent files
- Enable different teams to own different orchestrations
- Reload individual orchestrations without affecting others
- Better organize and maintain complex routing logic

### Configuration

Configure multiple orchestrations in `application.yml`:

```yaml
orchestrator:
  config:
    files:
      - name: "order-processing"
        path: "classpath:orchestrations/order-processing.yml"
      - name: "user-registration"
        path: "classpath:orchestrations/user-registration.yml"
      - name: "shipping"
        path: "classpath:orchestrations/shipping.yml"
    defaultOrchestration: "order-processing"
```

### Directory Structure

Organize orchestration files in a dedicated directory:

```
src/main/resources/
  └── orchestrations/
      ├── order-processing.yml
      ├── user-registration.yml
      └── shipping.yml
```

### Event Payload

Events specify which orchestration to use via the `orchestrationName` field:

```json
{
  "type": "OrderCreated",
  "orchestrationName": "order-processing",
  "correlationId": "order-12345",
  "context": {
    "orderId": "ORD-001"
  }
}
```

If `orchestrationName` is omitted or null, the default orchestration (specified in `defaultOrchestration`) will be used.

### Backward Compatibility

The system maintains full backward compatibility:

- **Legacy single-file mode**: If `orchestrator.config.file` is set and `orchestrator.config.files` is empty, the system operates in legacy mode
- **Events without orchestrationName**: Events with null/empty orchestration name use the default orchestration
- **Existing API endpoints**: All existing endpoints continue to work with the default orchestration

### Example: Multiple Workflows

**Order Processing Orchestration** (`orchestrations/order-processing.yml`):
```yaml
name: "Order Processing"
routes:
  - eventType: "OrderCreated"
    defaultTarget: "validation-service-queue"
  - eventType: "OrderValidated"
    defaultTarget: "inventory-service-queue"
```

**User Registration Orchestration** (`orchestrations/user-registration.yml`):
```yaml
name: "User Registration"
routes:
  - eventType: "UserRegistered"
    defaultTarget: "email-verification-queue"
  - eventType: "EmailVerified"
    defaultTarget: "profile-setup-queue"
```

### Migration Guide

See [MIGRATION_GUIDE.md](MIGRATION_GUIDE.md) for step-by-step instructions on migrating from single to multiple orchestrations.

## Examples

### Example 1: E-commerce Order Flow

```yaml
routes:
  - eventType: "OrderCreated"
    defaultTarget: "validation-queue"
  
  - eventType: "OrderValidated"
    defaultTarget: "inventory-queue"
  
  - eventType: "InventoryReserved"
    conditions:
      - condition: "#context['customerTier'] == 'premium'"
        target: "express-payment-queue"
    defaultTarget: "payment-queue"
  
  - eventType: "PaymentSucceeded"
    defaultTarget: "fulfillment-queue"
  
  - eventType: "PaymentFailed"
    conditions:
      - condition: "#context['inventoryReserved'] == true"
        target: "inventory-rollback-queue"
    defaultTarget: "notification-queue"
```

### Example 2: Document Processing

```yaml
routes:
  - eventType: "DocumentUploaded"
    conditions:
      - condition: "#context['fileType'] == 'pdf'"
        target: "pdf-processor-queue"
      - condition: "#context['fileType'] == 'docx'"
        target: "docx-processor-queue"
    defaultTarget: "generic-processor-queue"
  
  - eventType: "DocumentProcessed"
    conditions:
      - condition: "#context['requiresOCR'] == true"
        target: "ocr-service-queue"
    defaultTarget: "storage-queue"
```

### Example 3: Financial Transaction

```yaml
routes:
  - eventType: "TransactionInitiated"
    conditions:
      - condition: "#context['amount'] > 10000"
        target: "high-value-verification-queue"
        priority: 1
      - condition: "#context['internationalTransfer'] == true"
        target: "compliance-check-queue"
        priority: 2
    defaultTarget: "standard-processing-queue"
```

## Deployment

### Docker

The project includes an optimized multi-stage `Dockerfile` that builds a production-ready container image.

#### Using GitHub Container Registry (Recommended)

Images are automatically built and pushed to GitHub Container Registry (GHCR) on every push to the `main` branch via GitHub Actions.

**Pull and run the latest image:**

```bash
# Authenticate with GHCR (if image is private)
echo $GITHUB_TOKEN | docker login ghcr.io -u USERNAME --password-stdin

# Pull the image
docker pull ghcr.io/<owner>/stateless-orchestrator:latest

# Run with LocalStack (local development)
docker-compose up -d localstack
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=dev \
  -e LOCALSTACK_ENDPOINT=http://host.docker.internal:4566 \
  --network host \
  ghcr.io/<owner>/stateless-orchestrator:latest

# Run with AWS SQS (production)
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e AWS_REGION=us-east-1 \
  -e AWS_ACCESS_KEY_ID=your-key \
  -e AWS_SECRET_ACCESS_KEY=your-secret \
  -e SQS_QUEUE_URL_PREFIX=https://sqs.us-east-1.amazonaws.com/123456789012/ \
  ghcr.io/<owner>/stateless-orchestrator:latest
```

**Available image tags:**
- `latest` - Latest build from main branch
- `sha-<commit-sha>` - Specific commit (e.g., `sha-abc1234`)
- `v1.0.0`, `v1.0`, `v1` - Semantic version tags (when git tags are pushed)

#### Building Locally

**Local Development (LocalStack SQS):**
```bash
# Start LocalStack first
docker-compose up -d

# Build the image
docker build -t stateless-orchestrator .

# Run the orchestrator
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=dev \
  -e LOCALSTACK_ENDPOINT=http://host.docker.internal:4566 \
  --network host \
  stateless-orchestrator
```

**Production (AWS SQS):**
```bash
# Build the image
docker build -t stateless-orchestrator .

# Run with AWS credentials
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e AWS_REGION=us-east-1 \
  -e AWS_ACCESS_KEY_ID=your-key \
  -e AWS_SECRET_ACCESS_KEY=your-secret \
  -e SQS_QUEUE_URL_PREFIX=https://sqs.us-east-1.amazonaws.com/123456789012/ \
  stateless-orchestrator
```

**Using IAM role (recommended for ECS/EC2):**
```bash
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e AWS_REGION=us-east-1 \
  stateless-orchestrator
```

#### CI/CD with GitHub Actions

The repository includes a GitHub Actions workflow (`.github/workflows/docker-build-push.yml`) that:

- Automatically builds multi-architecture images (linux/amd64, linux/arm64) on push to `main`
- Pushes images to GitHub Container Registry
- Creates multiple tags (SHA, latest, semantic versions)
- Uses layer caching for faster builds

**Workflow triggers:**
- Push to `main` branch
- Manual workflow dispatch

**View images:** Navigate to `https://github.com/<owner>/<repo>/pkgs/container/stateless-orchestrator` in your repository.

### Kubernetes

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: stateless-orchestrator
spec:
  replicas: 3  # Horizontal scaling
  selector:
    matchLabels:
      app: orchestrator
  template:
    metadata:
      labels:
        app: orchestrator
    spec:
      containers:
      - name: orchestrator
        image: ghcr.io/<owner>/stateless-orchestrator:latest
        ports:
        - containerPort: 8080
        env:
        # For LocalStack SQS (dev)
        - name: SPRING_PROFILES_ACTIVE
          value: "dev"
        - name: LOCALSTACK_ENDPOINT
          value: "http://localstack-service:4566"
        # For AWS SQS (prod) - uncomment and configure:
        # - name: SPRING_PROFILES_ACTIVE
        #   value: "prod"
        # - name: AWS_REGION
        #   value: us-east-1
        # - name: SQS_QUEUE_URL_PREFIX
        #   value: https://sqs.us-east-1.amazonaws.com/123456789012/
        # Note: Use IAM service account for AWS credentials in production
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
```

### AWS Lambda (Serverless)

The orchestrator can be packaged for AWS Lambda using Spring Cloud Function:

1. Add dependency to `pom.xml`:
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-function-adapter-aws</artifactId>
</dependency>
```

2. Create Lambda handler
3. Deploy with SAM or CDK

## Monitoring

### LocalStack Queue Monitoring (Local Development)

When running locally with LocalStack, use the provided monitoring scripts to inspect queue contents and metrics:

- **Real-time Dashboard:** `./scripts/monitor-queues.sh` - Live monitoring of all queues
- **Queue Statistics:** `./scripts/queue-stats.sh` - One-time snapshot of queue metrics
- **Peek Messages:** `./scripts/peek-queue.sh <queue-name>` - View messages without consuming
- **Purge Queue:** `./scripts/purge-queue.sh <queue-name>` - Clear all messages from a queue

See the [LocalStack Setup](#localstack-setup-local-development) section for detailed usage.

### Metrics

Available at `/actuator/prometheus`:

- `orchestrator_events_processed_total` - Total events processed
- `orchestrator_events_routed_total` - Events successfully routed
- `orchestrator_events_failed_total` - Failed events
- `orchestrator_routing_time_seconds` - Routing latency

### Grafana Dashboard

Import the included `grafana-dashboard.json` for visualization.

### Alerts

Example Prometheus alert:

```yaml
- alert: HighOrchestrationFailureRate
  expr: rate(orchestrator_events_failed_total[5m]) > 0.1
  annotations:
    summary: High orchestration failure rate
```

## Best Practices

1. **Keep context minimal** - Only include data needed for routing decisions
2. **Use correlation IDs** - Track events through the entire system
3. **Enable audit logging** - Maintain event trail for debugging
4. **Version your config** - Use semantic versioning for orchestration config
5. **Test routing rules** - Use dry-run endpoint to validate
6. **Monitor metrics** - Track success/failure rates
7. **Use tags** - Organize routes with meaningful tags
8. **Document conditions** - Add descriptions to complex conditional routes

## Troubleshooting

### Events not routing

1. Check logs for routing errors
2. Verify event type matches exactly (case-sensitive)
3. Test with dry-run endpoint
4. Verify SQS connection (LocalStack or AWS)
5. Check queues exist: `aws --endpoint-url=http://localhost:4566 sqs list-queues` (for LocalStack)

### Configuration not loading

1. Check file path in `application.yml`
2. Validate YAML syntax
3. Check logs for parse errors

### High latency

1. Check SQS queue depths (LocalStack or AWS)
2. Review SpEL expression complexity
3. Increase orchestrator replicas
4. Monitor LocalStack/AWS SQS metrics

## Contributing

Contributions welcome! Please:
1. Fork the repository
2. Create a feature branch
3. Add tests
4. Submit a pull request

## License

MIT License - See LICENSE file for details

## Support

- Documentation: This README
- Issues: GitHub Issues
- Examples: `/examples` directory
