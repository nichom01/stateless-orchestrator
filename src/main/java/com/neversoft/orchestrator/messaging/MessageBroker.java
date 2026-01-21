package com.neversoft.orchestrator.messaging;

import java.util.Map;

/**
 * Abstraction for message broker operations.
 * Allows switching between RabbitMQ, SQS, Kafka, etc. via Spring profiles.
 */
public interface MessageBroker {
    
    /**
     * Send a message to a queue
     * 
     * @param queueName The name of the queue
     * @param message The message payload as JSON string
     */
    void sendToQueue(String queueName, String message);
    
    /**
     * Send a message to a queue with additional attributes/headers
     * 
     * @param queueName The name of the queue
     * @param message The message payload as JSON string
     * @param attributes Message attributes/headers (e.g., correlationId, eventId for tracing)
     */
    void sendToQueue(String queueName, String message, Map<String, String> attributes);
}
