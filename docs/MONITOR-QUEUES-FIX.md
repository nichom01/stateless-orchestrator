# monitor-queues.sh Fix - Attribute Names Issue

## Problem

`monitor-queues.sh` was showing different (incorrect) message counts compared to `queue-stats.sh`:

```bash
# queue-stats.sh showed:
validation-service-queue: 4 messages

# monitor-queues.sh showed:
validation-service-queue: 0 messages
```

## Root Cause

The two scripts were using different attribute name parameters:

**queue-stats.sh (correct):**
```bash
--attribute-names All
```

**monitor-queues.sh (incorrect):**
```bash
--attribute-names ApproximateNumberOfMessages,ApproximateNumberOfMessagesNotVisible,ApproximateNumberOfMessagesDelayed,ApproximateAgeOfOldestMessage
```

When specifying individual attribute names as a comma-separated list, LocalStack or certain AWS CLI versions may not return the expected results. Using `All` is more reliable and returns all queue attributes.

## Additional Issue

The script also had `set -e` at the top, which would cause it to exit on any command error. This was removed to make the script more resilient to non-critical errors.

## Fix Applied

1. **Changed attribute names to `All`:**
   ```bash
   attributes=$(docker exec localstack awslocal sqs get-queue-attributes \
       --queue-url "${queue_url}" \
       --attribute-names All \  # Changed from specific list
       2>/dev/null || echo "{}")
   ```

2. **Removed `set -e`:**
   ```bash
   # Before:
   set -e

   # After:
   # Note: set -e removed to prevent script from exiting on non-critical errors
   ```

3. **Added JSON compacting** (from previous fix):
   ```bash
   # Compact JSON to single line for consistent parsing
   attributes=$(echo "$attributes" | tr -d '\n' | tr -s ' ')
   ```

## After Fix

Both scripts now show identical results:

```bash
# queue-stats.sh
validation-service-queue                     4          0        0        -
inventory-service-queue                      1          0        0        -

# monitor-queues.sh
validation-service-queue                     4          0        0        - ⚡ Active
inventory-service-queue                      1          0        0        - ⚡ Active
```

## Usage

```bash
# Real-time monitoring (updates every 2 seconds)
./scripts/monitor-queues.sh

# Custom refresh interval
REFRESH_INTERVAL=5 ./scripts/monitor-queues.sh

# One-time snapshot
./scripts/queue-stats.sh
```

## Summary of All Script Fixes

Over this session, we fixed three issues with the queue monitoring scripts:

1. **Multi-line JSON parsing** - Scripts couldn't parse multi-line JSON, showed 0 for all queues
   - Fix: Compact JSON to single line with `tr -d '\n' | tr -s ' '`

2. **Queue URL resolution** - Scripts used wrong URL format
   - Fix: Get actual queue URLs from LocalStack instead of constructing them

3. **Attribute names** - monitor-queues.sh used specific attribute list that didn't work
   - Fix: Use `--attribute-names All` like queue-stats.sh

## Files Modified

- `scripts/monitor-queues.sh`
  - Removed `set -e`
  - Changed attribute names from specific list to `All`
  - Added JSON compacting
  - Fixed queue URL resolution

- `scripts/queue-stats.sh`
  - Added JSON compacting  
  - Fixed queue URL resolution

---

**Fixed:** January 21, 2026  
**Status:** ✅ Both scripts working correctly and showing identical results
