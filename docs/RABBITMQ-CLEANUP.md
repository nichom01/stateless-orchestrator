# RabbitMQ Configuration Cleanup

## Issue

The `application.yml` file contained RabbitMQ configuration that was no longer used after migrating to SQS.

```yaml
# Old application.yml had:
spring:
  # Default profile - use 'dev' for local development with RabbitMQ
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USERNAME:guest}
    password: ${RABBITMQ_PASSWORD:guest}
    listener:
      simple:
        acknowledge-mode: auto
        prefetch: 10
        retry:
          enabled: true
          max-attempts: 3
```

This was **completely unused** since:
1. RabbitMQ dependencies were removed from `pom.xml`
2. RabbitMQ consumer/broker classes were deleted
3. The project now uses SQS exclusively (with LocalStack for local dev)

## Changes Made

### 1. application.yml
**Removed:**
- Entire `spring.rabbitmq` section (14 lines)
- Outdated comment about RabbitMQ

**Updated:**
- Comment now says: "use 'dev' for local development with LocalStack, 'prod' for AWS"

### 2. application-dev.yml
**Removed:**
- Outdated comment: "RabbitMQ configuration kept for backward compatibility but not used when broker=sqs"

### 3. Verification
- ✅ No RabbitMQ/AMQP dependencies in `pom.xml`
- ✅ No RabbitMQ code in `src/main/java/`
- ✅ Project compiles successfully
- ✅ All tests pass

## Current Configuration Files

### application.yml (main)
```yaml
spring:
  application:
    name: stateless-orchestrator
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}

orchestrator:
  messaging:
    broker: ${MESSAGING_BROKER:sqs}  # Default: SQS
```

### application-dev.yml (LocalStack)
```yaml
spring:
  # LocalStack SQS Configuration for local development

orchestrator:
  messaging:
    broker: ${MESSAGING_BROKER:sqs}
    sqs:
      region: ${AWS_REGION:us-east-1}
      endpoint: ${LOCALSTACK_ENDPOINT:http://localhost:4566}
      queueUrlPrefix: ${SQS_QUEUE_URL_PREFIX:http://localhost:4566/000000000000/}
```

### application-prod.yml (AWS)
```yaml
orchestrator:
  messaging:
    broker: sqs
    sqs:
      region: ${AWS_REGION:us-east-1}
      queueUrlPrefix: ${SQS_QUEUE_URL_PREFIX:}
```

## How Configuration Loading Works in Spring Boot

### File Loading Order
Spring Boot loads configuration files in this order (later files override earlier):

1. `application.yml` - **Always loaded** (base config)
2. `application-{profile}.yml` - Profile-specific overrides
   - `application-dev.yml` when `SPRING_PROFILES_ACTIVE=dev`
   - `application-prod.yml` when `SPRING_PROFILES_ACTIVE=prod`

### Example
When running with `dev` profile:
```bash
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
```

Spring Boot loads:
1. `application.yml` (base)
2. `application-dev.yml` (overrides/adds to base)

Result:
- `spring.application.name` from `application.yml`
- `orchestrator.messaging.broker=sqs` from both (dev overrides if different)
- `orchestrator.messaging.sqs.endpoint=http://localhost:4566` from `application-dev.yml` only

## Why RabbitMQ Config Was Harmless (But Confusing)

The RabbitMQ configuration wasn't causing errors because:

1. **No RabbitMQ dependency** in `pom.xml`
   - Spring Boot doesn't try to connect to RabbitMQ if the library isn't present

2. **Profile-based bean loading**
   - `@Profile({"prod", "dev"})` on `SQSEventConsumer` and `SQSMessageBroker`
   - No beans with `@Profile("rabbitmq")` or `@ConditionalOnProperty("broker=rabbitmq")`

3. **Configuration properties are optional**
   - Spring Boot ignores configuration for features that aren't enabled

However, it was **confusing and misleading** to have it there, which is why we removed it.

## Summary

✅ **Removed:** Unused RabbitMQ configuration  
✅ **Updated:** Comments to reflect current architecture (SQS)  
✅ **Verified:** Project still builds and runs correctly  
✅ **Result:** Cleaner, more accurate configuration files  

---

**Cleaned Up:** January 21, 2026  
**Migration:** RabbitMQ → SQS (complete)
