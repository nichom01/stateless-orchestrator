package com.neversoft.orchestrator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Response model for bulk event submission
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkEventResponse {
    
    /**
     * Total number of events submitted
     */
    private int total;
    
    /**
     * Number of events successfully processed
     */
    private int successful;
    
    /**
     * Number of events that failed
     */
    private int failed;
    
    /**
     * List of failed event details (eventId and error message)
     */
    @Builder.Default
    private List<FailedEvent> failures = new ArrayList<>();
    
    /**
     * Processing duration in milliseconds
     */
    private long durationMs;
    
    /**
     * Represents a failed event with error details
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailedEvent {
        private String eventId;
        private String correlationId;
        private String type;
        private String error;
    }
}
