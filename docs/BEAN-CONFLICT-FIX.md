# Bean Conflict Fix - LocalStack SQS Configuration

## Issue
When running the application locally with the `dev` profile, you encountered this error:

```
The bean 'defaultSqsListenerContainerFactory', defined in class path resource 
[io/awspring/cloud/autoconfigure/sqs/SqsAutoConfiguration.class], could not be registered. 
A bean with that name has already been defined in class path resource 
[com/example/orchestrator/config/LocalStackSqsConfig.class] and overriding is disabled.
```

## Root Cause

The `LocalStackSqsConfig` class was manually defining a bean named `defaultSqsListenerContainerFactory`, which conflicted with the same bean automatically created by Spring Cloud AWS's `SqsAutoConfiguration`.

### Why This Happened

Spring Cloud AWS auto-configuration automatically creates the `defaultSqsListenerContainerFactory` bean when it detects an `SqsAsyncClient` bean in the context. Since we were providing a custom `SqsAsyncClient` for LocalStack, the auto-configuration would create the listener factory using our client. However, we were also manually defining the same bean, causing a conflict.

## Solution

**Removed the manual bean definition** from `LocalStackSqsConfig.java`:

```java
// ❌ REMOVED - This caused the conflict
@Bean
public SqsMessageListenerContainerFactory<?> defaultSqsListenerContainerFactory(SqsAsyncClient sqsAsyncClient) {
    return SqsMessageListenerContainerFactory.builder()
            .sqsAsyncClient(sqsAsyncClient)
            .build();
}
```

### What We Keep in LocalStackSqsConfig

The configuration now only defines the beans we need to customize for LocalStack:

1. **`sqsClient()`** - Synchronous client for queue management (QueueInitializer)
2. **`sqsAsyncClient()`** - Async client pointing to LocalStack endpoint
3. **`sqsTemplate()`** - Template for sending messages

### How It Works Now

1. We provide a custom `sqsAsyncClient` bean configured for LocalStack
2. Spring Cloud AWS auto-configuration detects our `sqsAsyncClient`
3. Auto-configuration automatically creates `defaultSqsListenerContainerFactory` using our client
4. The `@SqsListener` in `SQSEventConsumer` uses the auto-configured factory
5. Everything points to LocalStack at `http://localhost:4566` ✅

## Files Changed

- `src/main/java/com/example/orchestrator/config/LocalStackSqsConfig.java`

## Benefits

1. ✅ No bean conflicts
2. ✅ Leverages Spring Cloud AWS auto-configuration
3. ✅ Less code to maintain
4. ✅ Follows Spring Boot conventions
5. ✅ Still works with LocalStack perfectly

## Testing

```bash
# Compile
mvn clean compile

# Run tests
mvn test

# Run locally with LocalStack
docker-compose up -d
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
```

## Related Configuration

Make sure your `application-dev.yml` has:

```yaml
orchestrator:
  messaging:
    broker: sqs
    sqs:
      region: us-east-1
      endpoint: http://localhost:4566
```

## Notes

- The `defaultSqsListenerContainerFactory` name is a Spring Cloud AWS convention
- When you provide a custom `SqsAsyncClient`, auto-configuration uses it
- Bean overriding is disabled by default in Spring Boot 3+ (security best practice)
- This fix maintains all LocalStack functionality while following Spring Boot best practices
