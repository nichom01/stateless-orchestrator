# Migration Guide: Single to Multiple Orchestrations

This guide walks you through migrating from a single orchestration configuration file to multiple orchestration files.

## Overview

The orchestrator supports both legacy single-file mode and new multi-file mode. Migration is optional and can be done gradually - the system maintains full backward compatibility.

## Benefits of Multiple Orchestrations

- **Better organization**: Separate workflows into independent files
- **Team ownership**: Different teams can own different orchestrations
- **Selective reloading**: Reload individual orchestrations without affecting others
- **Easier maintenance**: Smaller, focused configuration files

## Migration Steps

### Step 1: Review Current Configuration

Examine your current `orchestration-config.yml` to identify logical groupings:

```yaml
# Current single file might contain:
routes:
  # Order processing routes
  - eventType: "OrderCreated"
    defaultTarget: "validation-service-queue"
  - eventType: "OrderValidated"
    defaultTarget: "inventory-service-queue"
  
  # User registration routes
  - eventType: "UserRegistered"
    defaultTarget: "email-verification-queue"
  - eventType: "EmailVerified"
    defaultTarget: "profile-setup-queue"
```

### Step 2: Create Orchestrations Directory

Create the orchestrations directory:

```bash
mkdir -p src/main/resources/orchestrations
```

### Step 3: Split Configuration Files

Split your routes into logical groups. For example:

**`orchestrations/order-processing.yml`:**
```yaml
name: "Order Processing"
version: "1.0.0"
description: "Routes events for e-commerce order processing"

settings:
  queuePrefix: "order-processing"
  auditEnabled: true
  metricsEnabled: true

routes:
  - eventType: "OrderCreated"
    defaultTarget: "validation-service-queue"
  
  - eventType: "OrderValidated"
    defaultTarget: "inventory-service-queue"
  
  - eventType: "InventoryReserved"
    defaultTarget: "payment-service-queue"
```

**`orchestrations/user-registration.yml`:**
```yaml
name: "User Registration"
version: "1.0.0"
description: "Routes events for user registration workflow"

settings:
  queuePrefix: "user-registration"
  auditEnabled: true
  metricsEnabled: true

routes:
  - eventType: "UserRegistered"
    defaultTarget: "email-verification-queue"
  
  - eventType: "EmailVerified"
    defaultTarget: "profile-setup-queue"
```

### Step 4: Update application.yml

Update your `application.yml` to use multiple files:

```yaml
orchestrator:
  config:
    # NEW: Multi-file configuration
    files:
      - name: "order-processing"
        path: "classpath:orchestrations/order-processing.yml"
      - name: "user-registration"
        path: "classpath:orchestrations/user-registration.yml"
    defaultOrchestration: "order-processing"
    
    # OLD: Comment out or remove legacy single-file config
    # file: classpath:orchestration-config.yml
```

### Step 5: Update Event Producers

Update your event producers to include `orchestrationName`:

**Before:**
```json
{
  "type": "OrderCreated",
  "correlationId": "order-12345",
  "context": { "orderId": "ORD-001" }
}
```

**After:**
```json
{
  "type": "OrderCreated",
  "orchestrationName": "order-processing",
  "correlationId": "order-12345",
  "context": { "orderId": "ORD-001" }
}
```

**Note:** If `orchestrationName` is omitted, the default orchestration will be used, so this step is optional for gradual migration.

### Step 6: Test the Migration

1. **Start the application:**
   ```bash
   mvn spring-boot:run
   ```

2. **Verify orchestrations are loaded:**
   ```bash
   curl http://localhost:8080/api/orchestrator/orchestrations
   ```
   
   Expected response:
   ```json
   {
     "orchestrations": ["order-processing", "user-registration"],
     "count": 2
   }
   ```

3. **Test routing with explicit orchestration:**
   ```bash
   curl -X POST http://localhost:8080/api/orchestrator/events/dry-run \
     -H "Content-Type: application/json" \
     -d '{
       "type": "OrderCreated",
       "orchestrationName": "order-processing",
       "correlationId": "test-123"
     }'
   ```

4. **Test routing with default orchestration:**
   ```bash
   curl -X POST http://localhost:8080/api/orchestrator/events/dry-run \
     -H "Content-Type: application/json" \
     -d '{
       "type": "OrderCreated",
       "correlationId": "test-123"
     }'
   ```

### Step 7: Gradual Migration (Optional)

You can migrate gradually:

1. **Phase 1**: Keep legacy config, add new orchestrations alongside
   ```yaml
   orchestrator:
     config:
       file: classpath:orchestration-config.yml  # Keep for now
       files:
         - name: "new-workflow"
           path: "classpath:orchestrations/new-workflow.yml"
   ```

2. **Phase 2**: Migrate events one workflow at a time
   - Start with new workflows using `orchestrationName`
   - Gradually update existing workflows
   - Remove legacy config when all workflows are migrated

3. **Phase 3**: Remove legacy configuration
   ```yaml
   orchestrator:
     config:
       # Remove: file: classpath:orchestration-config.yml
       files:
         - name: "order-processing"
           path: "classpath:orchestrations/order-processing.yml"
         - name: "user-registration"
           path: "classpath:orchestrations/user-registration.yml"
       defaultOrchestration: "order-processing"
   ```

## Rollback Procedure

If you need to rollback to single-file mode:

1. **Restore `application.yml`:**
   ```yaml
   orchestrator:
     config:
       file: classpath:orchestration-config.yml
   ```

2. **Remove or comment out multi-file configuration:**
   ```yaml
   orchestrator:
     config:
       # files:
       #   - name: "order-processing"
       #     path: "classpath:orchestrations/order-processing.yml"
       file: classpath:orchestration-config.yml
   ```

3. **Restart the application**

The system will automatically detect legacy mode and use the single file.

## Best Practices

### Naming Conventions

- Use kebab-case for orchestration names: `order-processing`, `user-registration`
- Keep names descriptive and consistent
- Match directory/file names where possible

### File Organization

```
src/main/resources/
  ├── orchestrations/
  │   ├── order-processing.yml
  │   ├── user-registration.yml
  │   ├── shipping.yml
  │   └── payment.yml
  └── orchestration-config.yml (legacy, can be removed after migration)
```

### Event Naming

- Use consistent event type naming within each orchestration
- Consider prefixing event types with orchestration name if there's overlap:
  - `order.OrderCreated` vs `user.UserCreated`
  - Or use different event names: `OrderCreated` vs `UserRegistered`

### Default Orchestration

- Set a sensible default orchestration for your most common workflow
- Events without `orchestrationName` will use the default

## Troubleshooting

### Orchestration Not Found

If you see warnings about orchestration not found:

1. **Check configuration:**
   ```bash
   curl http://localhost:8080/api/orchestrator/orchestrations
   ```

2. **Verify file paths** in `application.yml` are correct

3. **Check file exists** in `src/main/resources/orchestrations/`

### Events Routing to Wrong Orchestration

1. **Verify event has correct `orchestrationName`:**
   ```bash
   curl -X POST http://localhost:8080/api/orchestrator/events/dry-run \
     -H "Content-Type: application/json" \
     -d '{"type": "OrderCreated", "orchestrationName": "order-processing"}'
   ```

2. **Check default orchestration** is set correctly in `application.yml`

3. **Review logs** for orchestration selection messages

### Configuration Not Loading

1. **Check YAML syntax** - validate all orchestration files
2. **Verify file paths** are correct and files exist
3. **Check application logs** for loading errors
4. **Test individual files** by temporarily using single-file mode

## Example: Complete Migration

Here's a complete example of migrating a single orchestration to multiple:

**Before (`orchestration-config.yml`):**
```yaml
name: "Main Orchestration"
version: "1.0.0"
routes:
  - eventType: "OrderCreated"
    defaultTarget: "validation-queue"
  - eventType: "UserRegistered"
    defaultTarget: "email-queue"
```

**After (`orchestrations/order-processing.yml`):**
```yaml
name: "Order Processing"
version: "1.0.0"
routes:
  - eventType: "OrderCreated"
    defaultTarget: "validation-queue"
```

**After (`orchestrations/user-registration.yml`):**
```yaml
name: "User Registration"
version: "1.0.0"
routes:
  - eventType: "UserRegistered"
    defaultTarget: "email-queue"
```

**After (`application.yml`):**
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

## Support

For questions or issues during migration:

1. Check the [README.md](README.md) for detailed documentation
2. Review application logs for error messages
3. Use the dry-run endpoint to test routing before deploying
4. Test in a development environment first
