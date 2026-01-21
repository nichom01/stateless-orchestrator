package com.neversoft.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Represents an event flowing through the orchestrator.
 * Events are immutable once created and carry all context needed for routing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Event {
    
    /**
     * Unique identifier for this event instance
     */
    @Builder.Default
    private String eventId = UUID.randomUUID().toString();
    
    /**
     * Type of event (e.g., "OrderCreated", "PaymentSucceeded")
     */
    private String type;
    
    /**
     * Timestamp when event was created
     */
    @Builder.Default
    private Instant timestamp = Instant.now();
    
    /**
     * Context data needed for routing decisions and service processing.
     * This contains all the state that would normally be in a stateful orchestrator's database.
     */
    @Builder.Default
    private Map<String, Object> context = new HashMap<>();
    
    /**
     * Correlation ID for tracking related events through the system
     */
    private String correlationId;
    
    /**
     * Name of the orchestration configuration to use for routing this event.
     * If null, the default orchestration will be used.
     */
    private String orchestrationName;
    
    /**
     * Source service that emitted this event
     */
    private String source;
    
    /**
     * Optional metadata for tracking, debugging, etc.
     */
    @Builder.Default
    private Map<String, String> metadata = new HashMap<>();
    
    /**
     * Version of the event schema (for handling evolution)
     */
    @Builder.Default
    private String version = "1.0";
    
    /**
     * Helper method to get context value with type casting
     */
    @SuppressWarnings("unchecked")
    public <T> T getContextValue(String key, Class<T> type) {
        Object value = context.get(key);
        if (value == null) {
            return null;
        }
        return (T) value;
    }
    
    /**
     * Helper method to get context value with default
     */
    @SuppressWarnings("unchecked")
    public <T> T getContextValue(String key, T defaultValue) {
        Object value = context.get(key);
        if (value == null) {
            return defaultValue;
        }
        return (T) value;
    }
    
    /**
     * Create a new event with updated context (immutable pattern)
     */
    public Event withContext(Map<String, Object> newContext) {
        return Event.builder()
                .eventId(UUID.randomUUID().toString())
                .type(this.type)
                .timestamp(Instant.now())
                .context(new HashMap<>(newContext))
                .correlationId(this.correlationId)
                .orchestrationName(this.orchestrationName)
                .source(this.source)
                .metadata(new HashMap<>(this.metadata))
                .version(this.version)
                .build();
    }
    
    /**
     * Create a new event with a different type but same context
     */
    public Event withType(String newType) {
        return Event.builder()
                .eventId(UUID.randomUUID().toString())
                .type(newType)
                .timestamp(Instant.now())
                .context(new HashMap<>(this.context))
                .correlationId(this.correlationId)
                .orchestrationName(this.orchestrationName)
                .source(this.source)
                .metadata(new HashMap<>(this.metadata))
                .version(this.version)
                .build();
    }
}
