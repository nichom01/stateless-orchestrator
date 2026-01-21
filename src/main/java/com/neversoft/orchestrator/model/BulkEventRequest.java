package com.neversoft.orchestrator.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request model for bulk event submission
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkEventRequest {
    
    /**
     * List of events to process
     */
    private List<Event> events;
}
