package com.neversoft.orchestrator.service;

import com.neversoft.orchestrator.model.Event;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Audit service for logging all event activity
 * In production, this would write to a database or audit log system
 */
@Service
@Slf4j
public class AuditService {
    
    /**
     * Log an event with its status
     * This is async to not block the main orchestration flow
     */
    @Async
    public void logEvent(Event event, String status, String details) {
        AuditEntry entry = AuditEntry.builder()
                .eventId(event.getEventId())
                .eventType(event.getType())
                .orchestrationName(event.getOrchestrationName())
                .correlationId(event.getCorrelationId())
                .status(status)
                .details(details)
                .timestamp(Instant.now())
                .build();
        
        // In production: save to database
        log.info("AUDIT | EventId: {} | Type: {} | Orchestration: {} | CorrelationId: {} | Status: {} | Details: {} | Timestamp: {}", 
                entry.getEventId(),
                entry.getEventType(),
                entry.getOrchestrationName(),
                entry.getCorrelationId(),
                entry.getStatus(),
                entry.getDetails(),
                entry.getTimestamp());
    }
    
    @lombok.Builder
    @lombok.Data
    private static class AuditEntry {
        private String eventId;
        private String eventType;
        private String orchestrationName;
        private String correlationId;
        private String status;
        private String details;
        private Instant timestamp;
    }
}
