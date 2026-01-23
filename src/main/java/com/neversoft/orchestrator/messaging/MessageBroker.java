package com.neversoft.orchestrator.messaging;

import java.util.List;
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
    
    /**
     * Send multiple messages to a queue in a batch (for performance optimization)
     * Each message can have its own attributes map
     * 
     * @param queueName The name of the queue
     * @param messages List of message payloads as JSON strings
     * @param attributesList List of attributes maps (one per message, can be null)
     */
    void sendBatchToQueue(String queueName, List<String> messages, List<Map<String, String>> attributesList);
}
