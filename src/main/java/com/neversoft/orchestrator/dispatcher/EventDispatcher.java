package com.neversoft.orchestrator.dispatcher;

import com.neversoft.orchestrator.messaging.MessageBroker;
import com.neversoft.orchestrator.model.Event;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Dispatches events to target queues/services
 * Uses MessageBroker abstraction to support RabbitMQ, SQS, etc. via Spring profiles
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EventDispatcher {
    
    private final MessageBroker messageBroker;
    private final ObjectMapper objectMapper;
    
    /**
     * Send event to target queue
     */
    public void dispatch(Event event, String target) {
        try {
            log.info("Dispatching event: type={}, target={}, correlationId={}", 
                    event.getType(), target, event.getCorrelationId());
            
            String eventJson = objectMapper.writeValueAsString(event);
            
            // Build message attributes for tracing
            Map<String, String> attributes = buildMessageAttributes(event);
            
            // Send via message broker abstraction
            messageBroker.sendToQueue(target, eventJson, attributes);
            
            log.debug("Event dispatched successfully: eventId={}", event.getEventId());
            
        } catch (Exception e) {
            log.error("Failed to dispatch event: eventId={}, target={}", 
                    event.getEventId(), target, e);
            throw new RuntimeException("Failed to dispatch event", e);
        }
    }
    
    /**
     * Build message attributes from event metadata for tracing
     */
    private Map<String, String> buildMessageAttributes(Event event) {
        Map<String, String> attributes = new HashMap<>();
        
        if (event.getEventId() != null) {
            attributes.put("eventId", event.getEventId());
        }
        if (event.getCorrelationId() != null) {
            attributes.put("correlationId", event.getCorrelationId());
        }
        if (event.getType() != null) {
            attributes.put("eventType", event.getType());
        }
        if (event.getSource() != null) {
            attributes.put("source", event.getSource());
        }
        if (event.getVersion() != null) {
            attributes.put("version", event.getVersion());
        }
        
        return attributes;
    }
}
