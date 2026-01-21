# SQS Scripts Usage Guide

All scripts in the `scripts/` directory now work with or without AWS CLI installed on your host machine. They automatically detect and use LocalStack's built-in `awslocal` command via Docker if AWS CLI is not available.

## Prerequisites

### Required
- Docker running
- LocalStack container running: `docker-compose up -d`

### Optional
- AWS CLI (if not installed, scripts use Docker exec automatically)

## Available Scripts

### 1. queue-stats.sh
Shows a snapshot of all queue statistics.

```bash
# Table output (default)
./scripts/queue-stats.sh

# JSON output
./scripts/queue-stats.sh --json
```

**Output:**
```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    LocalStack SQS Queue Statistics                              │
│                    Snapshot: 2026-01-21 00:05:41                            │
└─────────────────────────────────────────────────────────────────────────────────┘

QUEUE NAME                           AVAILABLE  IN-FLIGHT  DELAYED   AGE(s)
────────────────────────────────────────────────────────────────────────────────────────────────
orchestrator-input                           0          0        0        -
validation-service-queue                     0          0        0        -
...
────────────────────────────────────────────────────────────────────────────────────────────────
TOTAL                                        0          0        0
```

### 2. monitor-queues.sh
Real-time monitoring dashboard that updates every 2 seconds.

```bash
# Default: updates every 2 seconds
./scripts/monitor-queues.sh

# Custom refresh interval (in seconds)
REFRESH_INTERVAL=5 ./scripts/monitor-queues.sh
```

Press `Ctrl+C` to exit.

**Output:**
```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    LocalStack SQS Queue Monitor                                  │
│                    Last Updated: 2026-01-21 00:05:41                            │
└─────────────────────────────────────────────────────────────────────────────────┘

QUEUE NAME                           AVAILABLE  IN-FLIGHT  DELAYED   AGE(s)     STATUS
────────────────────────────────────────────────────────────────────────────────────────────────
orchestrator-input                           0          0        0        -    ✓ Idle
validation-service-queue                     5          2        0       45    ⚡ Active
...

[Press Ctrl+C to quit | Updates every 2s]
```

### 3. peek-queue.sh
View messages in a queue without consuming them (visibility timeout = 0).

```bash
# Peek at up to 10 messages (default)
./scripts/peek-queue.sh <queue-name>

# Peek at specific number of messages (max 10)
./scripts/peek-queue.sh <queue-name> 5
```

**Examples:**
```bash
# View messages in orchestrator input queue
./scripts/peek-queue.sh orchestrator-input

# View up to 3 messages in validation queue
./scripts/peek-queue.sh validation-service-queue 3
```

**Output:**
```
Peeking at messages in queue: orchestrator-input
Queue URL: http://localhost:4566/000000000000/orchestrator-input

Found 2 message(s):

--- Message 1 ---
Message ID: abc123-def456-ghi789
Receipt Handle: AQEBxyz...
Attributes:
  SentTimestamp: 1705881234567
  ApproximateReceiveCount: 1
Body:
{
  "eventType": "OrderCreated",
  "eventId": "order-123",
  "timestamp": "2026-01-21T00:05:00Z",
  "payload": {
    "orderId": "ORD-123",
    "amount": 100.00
  }
}

--- Message 2 ---
...
```

### 4. purge-queue.sh
Delete all messages from a queue.

```bash
# With confirmation prompt
./scripts/purge-queue.sh <queue-name>

# Skip confirmation
./scripts/purge-queue.sh <queue-name> --confirm
```

**Examples:**
```bash
# Purge with confirmation
./scripts/purge-queue.sh orchestrator-input

# Purge without confirmation
./scripts/purge-queue.sh validation-service-queue --confirm
```

**Output:**
```
Warning: This will delete ALL messages from queue 'orchestrator-input'
Current messages: 15 (12 available, 3 in-flight)

Are you sure you want to purge this queue? (yes/no): yes

Purging queue 'orchestrator-input'...
✓ Queue purged successfully
Note: It may take up to 60 seconds for the purge to complete
```

## Queue Names

All scripts recognize these queue names (from orchestration configuration):

- `orchestrator-input`
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

## AWS CLI vs Docker Exec

### With AWS CLI Installed

Scripts use AWS CLI directly:
```bash
aws --endpoint-url=http://localhost:4566 sqs list-queues
```

### Without AWS CLI (Automatic Fallback)

Scripts automatically use LocalStack's `awslocal` via Docker:
```bash
docker exec localstack awslocal sqs list-queues
```

**Note:** You'll see this message when using Docker fallback:
```
Note: Using LocalStack's awslocal via Docker (AWS CLI not found on host)
```

## Environment Variables

All scripts support these environment variables:

```bash
# LocalStack endpoint (default: http://localhost:4566)
export LOCALSTACK_ENDPOINT=http://localhost:4566

# AWS region (default: us-east-1)
export AWS_REGION=us-east-1

# Refresh interval for monitor-queues.sh (default: 2 seconds)
export REFRESH_INTERVAL=5
```

## Troubleshooting

### Error: AWS CLI is not installed and LocalStack container is not running

**Solution:**
```bash
# Option 1: Start LocalStack
docker-compose up -d

# Option 2: Install AWS CLI
brew install awscli  # macOS
# or
pip install awscli  # Python
```

### Error: Cannot connect to LocalStack

**Check LocalStack is running:**
```bash
docker ps | grep localstack
curl http://localhost:4566/_localstack/health
```

**Restart LocalStack:**
```bash
docker-compose down
docker-compose up -d
```

### Error: Queue not found

**List available queues:**
```bash
docker exec localstack awslocal sqs list-queues

# Or with AWS CLI
aws --endpoint-url=http://localhost:4566 sqs list-queues --region us-east-1
```

**Ensure application is running:**
```bash
# The application creates queues on startup
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
```

## Script Permissions

If you get permission denied errors:

```bash
# Make scripts executable
chmod +x scripts/*.sh
```

## Integration with CI/CD

All scripts work in CI/CD environments where AWS CLI might not be pre-installed:

```yaml
# GitHub Actions example
steps:
  - name: Start LocalStack
    run: docker-compose up -d
    
  - name: Wait for LocalStack
    run: |
      timeout 30 sh -c 'until curl -s http://localhost:4566/_localstack/health; do sleep 1; done'
  
  - name: Check queue stats
    run: ./scripts/queue-stats.sh --json
```

## Quick Reference

```bash
# Monitor all queues in real-time
./scripts/monitor-queues.sh

# Get snapshot of all queues
./scripts/queue-stats.sh

# View messages without consuming
./scripts/peek-queue.sh orchestrator-input

# Clear all messages from a queue
./scripts/purge-queue.sh orchestrator-input --confirm

# Export stats as JSON
./scripts/queue-stats.sh --json > stats.json
```

---

**Last Updated:** January 21, 2026  
**Status:** ✅ All scripts working with automatic AWS CLI/Docker fallback
