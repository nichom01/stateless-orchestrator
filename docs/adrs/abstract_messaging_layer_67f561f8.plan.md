---
name: Abstract Messaging Layer
overview: Create a message broker abstraction layer to support both RabbitMQ (dev) and AWS SQS (production) from the same codebase using Spring profiles.
todos:
  - id: create-interface
    content: Create MessageBroker interface with sendToQueue methods
    status: completed
  - id: implement-rabbitmq
    content: Implement RabbitMQMessageBroker with @Profile("dev")
    status: completed
  - id: implement-sqs
    content: Implement SQSMessageBroker with @Profile("prod") and SQSConfig
    status: completed
  - id: refactor-dispatcher
    content: Refactor EventDispatcher to use MessageBroker abstraction
    status: completed
  - id: abstract-consumers
    content: Create profile-specific event consumers for RabbitMQ and SQS
    status: completed
  - id: config-properties
    content: Create MessagingProperties and profile-specific application YAMLs
    status: completed
  - id: update-docs
    content: Update README with multi-broker setup instructions
    status: completed
  - id: test-both-profiles
    content: Test application with both dev and prod profiles
    status: completed
---

# Abstract Messaging Layer for RabbitMQ and SQS

## Overview

Implement the Strategy pattern with Spring profiles to abstract the messaging infrastructure. This allows seamless switching between RabbitMQ (local development) and AWS SQS (production) without code changes.

## Current State

- [`EventDispatcher`](src/main/java/com/example/orchestrator/dispatcher/EventDispatcher.java) has hard dependency on `RabbitTemplate` (lines 19, 33, 54)
- [`EventConsumer`](src/main/java/com/example/orchestrator/consumer/EventConsumer.java) uses `@RabbitListener` annotation (line 25)
- Both RabbitMQ and SQS dependencies already present in [`pom.xml`](pom.xml)

## Architecture Diagram

```mermaid
graph TB
    OrchestratorService[OrchestratorService]
    EventDispatcher[EventDispatcher]
    MessageBroker[MessageBroker Interface]
    
    RabbitImpl[RabbitMQMessageBroker]
    SQSImpl[SQSMessageBroker]
    
    RabbitMQ[(RabbitMQ)]
    SQS[(AWS SQS)]
    
    OrchestratorService --> EventDispatcher
    EventDispatcher --> MessageBroker
    MessageBroker -.->|@Profile dev| RabbitImpl
    MessageBroker -.->|@Profile prod| SQSImpl
    
    RabbitImpl --> RabbitMQ
    SQSImpl --> SQS
```

## Implementation Plan

### 1. Create Message Broker Abstraction

**File:** `src/main/java/com/example/orchestrator/messaging/MessageBroker.java`

Create interface with methods:
- `void sendToQueue(String queueName, String message)`
- `void sendToQueue(String queueName, String message, Map<String, String> attributes)` (for message attributes/headers)

This interface abstracts all queue operations regardless of underlying technology.

### 2. Implement RabbitMQ Adapter

**File:** `src/main/java/com/example/orchestrator/messaging/rabbitmq/RabbitMQMessageBroker.java`

- Annotate with `@Service` and `@Profile("dev")`
- Inject `RabbitTemplate`
- Implement `MessageBroker` interface
- Convert message attributes to RabbitMQ message properties
- Handle queue name resolution (use as-is for direct queue sending)

### 3. Implement SQS Adapter

**File:** `src/main/java/com/example/orchestrator/messaging/sqs/SQSMessageBroker.java`

- Annotate with `@Service` and `@Profile("prod")`
- Inject `SqsTemplate` from Spring Cloud AWS
- Implement `MessageBroker` interface
- Convert message attributes to SQS message attributes
- Handle queue URL resolution (may need to construct full ARN/URL from queue name)

### 4. Create SQS Configuration

**File:** `src/main/java/com/example/orchestrator/messaging/sqs/SQSConfig.java`

- Annotate with `@Configuration` and `@Profile("prod")`
- Configure `SqsTemplate` bean
- Configure AWS credentials provider (DefaultAWSCredentialsProviderChain for production)
- Configure region from properties
- Optionally configure queue name to URL mapping

### 5. Refactor EventDispatcher

**File:** [`src/main/java/com/example/orchestrator/dispatcher/EventDispatcher.java`](src/main/java/com/example/orchestrator/dispatcher/EventDispatcher.java)

Changes:
- Replace `RabbitTemplate` dependency with `MessageBroker` interface (line 19)
- Update `dispatch()` method to use `messageBroker.sendToQueue()` (line 33)
- Remove second dispatch method with exchange/routingKey (lines 44-63) or adapt it
- Add event metadata as message attributes (correlationId, eventId, etc.)

### 6. Abstract Event Consumer

Since `@RabbitListener` is RabbitMQ-specific, we need profile-specific consumers:

**File:** `src/main/java/com/example/orchestrator/consumer/rabbitmq/RabbitMQEventConsumer.java`
- Annotate with `@Component` and `@Profile("dev")`
- Keep `@RabbitListener` annotation
- Delegate to shared processing logic in `OrchestratorService`

**File:** `src/main/java/com/example/orchestrator/consumer/sqs/SQSEventConsumer.java`
- Annotate with `@Component` and `@Profile("prod")`
- Use `@SqsListener` annotation from Spring Cloud AWS
- Delegate to shared processing logic in `OrchestratorService`

### 7. Create Profile-Specific Configuration

**File:** `src/main/resources/application-dev.yml`

```yaml
spring:
  profiles:
    active: dev
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest

orchestrator:
  messaging:
    broker: rabbitmq
```

**File:** `src/main/resources/application-prod.yml`

```yaml
spring:
  profiles:
    active: prod

orchestrator:
  messaging:
    broker: sqs
    sqs:
      region: us-east-1
      queueUrlPrefix: https://sqs.us-east-1.amazonaws.com/123456789012/
```

**File:** Update [`src/main/resources/application.yml`](src/main/resources/application.yml)

Add:
```yaml
orchestrator:
  messaging:
    # Default to dev profile
    broker: ${MESSAGING_BROKER:rabbitmq}
```

### 8. Add Configuration Properties

**File:** `src/main/java/com/example/orchestrator/config/MessagingProperties.java`

- Create `@ConfigurationProperties("orchestrator.messaging")` class
- Fields: `broker`, `sqs.region`, `sqs.queueUrlPrefix`
- Use for SQS configuration

### 9. Update README

Update [`README.md`](README.md) sections:
- Add "Multi-Broker Support" section
- Document profile activation: `-Dspring.profiles.active=dev` or `prod`
- Add AWS SQS setup instructions for production
- Add environment variable configuration for production (AWS credentials, region)

## Key Design Decisions

1. **Strategy Pattern**: `MessageBroker` interface with multiple implementations selected via Spring profiles
2. **Profile-based activation**: Use `dev` for RabbitMQ, `prod` for SQS
3. **Zero code changes for deployment**: Switch profiles via environment variable
4. **Message attributes**: Pass event metadata (correlationId, eventId) as message attributes for tracing
5. **Queue naming**: Keep queue names as-is in config; adapter handles URL/ARN resolution

## Testing Strategy

1. Run with dev profile (default): `mvn spring-boot:run`
2. Run with prod profile: `mvn spring-boot:run -Dspring.profiles.active=prod`
3. Add integration tests for both broker implementations
4. Use LocalStack or Testcontainers for SQS integration tests

## Benefits

- Single codebase supports multiple environments
- Easy to add new brokers (Kafka, Azure Service Bus) by implementing `MessageBroker`
- No conditional logic scattered through code
- Profile-specific dependencies only loaded when needed
- Configuration externalized for different environments