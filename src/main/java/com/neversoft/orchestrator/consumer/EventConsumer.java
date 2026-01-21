package com.neversoft.orchestrator.consumer;

/**
 * Legacy event consumer - replaced by profile-specific consumers:
 * - RabbitMQEventConsumer for dev profile
 * - SQSEventConsumer for prod profile
 * 
 * This class is kept for backward compatibility but should not be used.
 * Use the profile-specific consumers instead.
 * 
 * @deprecated Use RabbitMQEventConsumer or SQSEventConsumer based on profile
 */
@Deprecated
public class EventConsumer {
    // This class has been replaced by profile-specific consumers
    // See: RabbitMQEventConsumer (dev profile) and SQSEventConsumer (prod profile)
}
