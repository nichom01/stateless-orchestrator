package com.neversoft.orchestrator.dispatcher;

import com.neversoft.orchestrator.messaging.MessageBroker;
import com.neversoft.orchestrator.model.Event;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
            log.debug("Dispatching event: type={}, target={}, correlationId={}", 
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
     * Dispatch multiple events to their respective target queues in batches
     * Groups events by target queue and sends in batches for optimal performance
     */
    public void dispatchBatch(List<Event> events, List<String> targets) {
        if (events == null || events.isEmpty() || targets == null || targets.size() != events.size()) {
            throw new IllegalArgumentException("Events and targets lists must be non-empty and same size");
        }
        
        try {
            // Group events by target queue
            Map<String, List<Event>> eventsByQueue = new HashMap<>();
            for (int i = 0; i < events.size(); i++) {
                String target = targets.get(i);
                eventsByQueue.computeIfAbsent(target, k -> new ArrayList<>()).add(events.get(i));
            }
            
            log.debug("Dispatching batch of {} events to {} queues", events.size(), eventsByQueue.size());
            
            // Send each queue's events in a batch
            for (Map.Entry<String, List<Event>> entry : eventsByQueue.entrySet()) {
                String target = entry.getKey();
                List<Event> queueEvents = entry.getValue();
                
                List<String> messages = queueEvents.stream()
                    .map(event -> {
                        try {
                            return objectMapper.writeValueAsString(event);
                        } catch (Exception e) {
                            log.error("Failed to serialize event: {}", event.getEventId(), e);
                            return null;
                        }
                    })
                    .filter(msg -> msg != null)
                    .collect(Collectors.toList());
                
                List<Map<String, String>> attributesList = queueEvents.stream()
                    .map(this::buildMessageAttributes)
                    .collect(Collectors.toList());
                
                if (!messages.isEmpty()) {
                    messageBroker.sendBatchToQueue(target, messages, attributesList);
                }
            }
            
            log.debug("Batch dispatch completed: {} events", events.size());
            
        } catch (Exception e) {
            log.error("Failed to dispatch batch of events", e);
            throw new RuntimeException("Failed to dispatch batch", e);
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
