# Queue Monitor Fix - Multi-line JSON Parsing Issue

## Problem

When sending events through the orchestrator, messages were successfully routed to target queues, but the queue monitor scripts (`queue-stats.sh` and `monitor-queues.sh`) showed 0 messages for all queues.

```bash
# Before fix:
QUEUE NAME                           AVAILABLE  IN-FLIGHT  DELAYED   AGE(s)
────────────────────────────────────────────────────────────────────────────────────────────────
validation-service-queue                     0          0        0        -
inventory-service-queue                      0          0        0        -
...
TOTAL                                        0          0        0
```

## Root Cause

The AWS CLI returns JSON responses with multi-line formatting:

```json
{
    "Attributes": {
        "ApproximateNumberOfMessages": "3",
        "ApproximateNumberOfMessagesNotVisible": "0"
    }
}
```

The scripts collected this multi-line JSON into an array using the format: `queue-name|{json}`

Later, when parsing, the script used:
```bash
IFS='|' read -r queue_name stats <<< "$array_element"
```

**The Problem:** Bash's `read` command only reads **one line** at a time. So with multi-line JSON, `stats` would only get the first line after the `|`, which was just `{`, not the full JSON object.

This caused all the subsequent `grep` commands to fail, returning empty strings, which defaulted to "0".

## Solution

Compact the JSON to a single line before storing in the array:

```bash
# Before:
echo "$attributes"

# After:
echo "$attributes" | tr -d '\n' | tr -s ' '
```

This transforms:
```json
{
    "Attributes": {
        "ApproximateNumberOfMessages": "3"
    }
}
```

Into:
```json
{ "Attributes": { "ApproximateNumberOfMessages": "3" } }
```

Now when `read` splits on `|`, it gets the entire JSON string in one line, and the `grep` commands can successfully extract the values.

## Files Fixed

1. `scripts/queue-stats.sh` - Modified `get_queue_stats()` function
2. `scripts/monitor-queues.sh` - Modified `get_queue_stats()` function

## After Fix

```bash
# After fix:
QUEUE NAME                           AVAILABLE  IN-FLIGHT  DELAYED   AGE(s)
────────────────────────────────────────────────────────────────────────────────────────────────
orchestrator-input                           0          0        0        -
validation-service-queue                     3          0        0        -
inventory-service-queue                      1          0        0        -
...
TOTAL                                        4          0        0
```

## How The System Works

### Event Flow

1. **Send Event via REST API**
   ```bash
   curl -X POST http://localhost:8080/api/orchestrator/events \
     -H "Content-Type: application/json" \
     -d '{"type": "OrderCreated", ...}'
   ```

2. **Controller processes synchronously**
   - `OrchestratorController.submitEvent()` → `OrchestratorService.processEvent()`

3. **Routing Engine determines target**
   - Based on event type and conditions
   - Returns target queue name (e.g., `validation-service-queue`)

4. **Event Dispatcher sends to SQS**
   - `EventDispatcher.dispatch()` → `SQSMessageBroker.sendToQueue()`
   - Message sent to LocalStack SQS

5. **Message accumulates in target queue**
   - No listener on target queues (only on `orchestrator-input`)
   - Messages remain until consumed by downstream services

6. **Monitor shows queue stats**
   - Scripts query LocalStack SQS for message counts
   - Now correctly displays the counts!

### Why Messages Accumulate

The orchestrator has an SQS listener on the **input queue** only:

```java
@SqsListener("${orchestrator.queue.input:orchestrator-input}")
public void handleEvent(@Payload String message) {
    // Processes and routes to target queues
}
```

Target queues (validation-service-queue, inventory-service-queue, etc.) **don't have listeners**, so messages accumulate there waiting for downstream services to consume them.

This is the intended design - the orchestrator routes events, and separate microservices consume from their respective queues.

## Testing

### Send Test Event

```bash
curl -X POST http://localhost:8080/api/orchestrator/events \
  -H "Content-Type: application/json" \
  -d '{
    "type": "OrderCreated",
    "eventId": "test-123",
    "timestamp": "2026-01-21T00:00:00Z",
    "correlationId": "test-correlation",
    "payload": {"orderId": "ORD-123", "amount": 100.00},
    "metadata": {"source": "test"}
  }'
```

### Check Queue Stats

```bash
./scripts/queue-stats.sh
```

Expected output:
- Event routed to `validation-service-queue` (based on OrderCreated event type)
- Message count increments

### View Message Content

```bash
./scripts/peek-queue.sh validation-service-queue 1
```

Shows the actual event JSON stored in the queue.

### Monitor in Real-Time

```bash
./scripts/monitor-queues.sh
```

Updates every 2 seconds showing live message counts.

## Additional Fixes in This Session

Along with fixing the multi-line JSON issue, we also:

1. **Fixed Queue URL Resolution**
   - Updated scripts to get actual queue URLs from LocalStack
   - LocalStack returns: `http://sqs.us-east-1.localhost.localstack.cloud:4566/...`
   - Scripts were constructing: `http://localhost:4566/...`

2. **Added Docker Fallback**
   - Scripts now work without AWS CLI on host
   - Automatically use `docker exec localstack awslocal` if AWS CLI not found

## Verification

```bash
# 1. Start LocalStack
docker-compose up -d

# 2. Start application
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run

# 3. Send test event
curl -X POST http://localhost:8080/api/orchestrator/events \
  -H "Content-Type: application/json" \
  -d '{"type": "OrderCreated", "eventId": "test-1", "correlationId": "test-1", ...}'

# 4. Check queue stats
./scripts/queue-stats.sh
# Should show 1 message in validation-service-queue

# 5. View the message
./scripts/peek-queue.sh validation-service-queue 1
# Should show the OrderCreated event JSON
```

---

**Fixed:** January 21, 2026  
**Status:** ✅ All queue monitoring scripts working correctly  
**Affected Scripts:** `queue-stats.sh`, `monitor-queues.sh`
