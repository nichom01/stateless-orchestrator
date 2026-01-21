package com.neversoft.orchestrator.service;

import com.neversoft.orchestrator.dispatcher.EventDispatcher;
import com.neversoft.orchestrator.model.Event;
import com.neversoft.orchestrator.routing.RoutingEngine;
import com.neversoft.orchestrator.routing.RoutingResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Main orchestrator service that coordinates routing and dispatching
 */
@Service
@Slf4j
public class OrchestratorService {
    
    private final RoutingEngine routingEngine;
    private final EventDispatcher eventDispatcher;
    private final AuditService auditService;
    
    // Metrics
    private final Counter eventsProcessed;
    private final Counter eventsRouted;
    private final Counter eventsFailed;
    private final Timer routingTimer;
    
    public OrchestratorService(RoutingEngine routingEngine,
                              EventDispatcher eventDispatcher,
                              AuditService auditService,
                              MeterRegistry meterRegistry) {
        this.routingEngine = routingEngine;
        this.eventDispatcher = eventDispatcher;
        this.auditService = auditService;
        
        // Initialize metrics
        this.eventsProcessed = Counter.builder("orchestrator.events.processed")
                .description("Total events processed")
                .register(meterRegistry);
        
        this.eventsRouted = Counter.builder("orchestrator.events.routed")
                .description("Events successfully routed")
                .register(meterRegistry);
        
        this.eventsFailed = Counter.builder("orchestrator.events.failed")
                .description("Events that failed to route")
                .register(meterRegistry);
        
        this.routingTimer = Timer.builder("orchestrator.routing.time")
                .description("Time taken to route events")
                .register(meterRegistry);
    }
    
    /**
     * Process an incoming event: route it and dispatch to target
     */
    public void processEvent(Event event) {
        log.info("Processing event: type={}, orchestration={}, eventId={}, correlationId={}", 
                event.getType(), event.getOrchestrationName(), event.getEventId(), event.getCorrelationId());
        
        eventsProcessed.increment();
        
        try {
            // Audit the incoming event
            auditService.logEvent(event, "RECEIVED", null);
            
            // Route the event
            RoutingResult result = routingTimer.record(() -> routingEngine.route(event));
            
            if (result.isSuccess()) {
                // Dispatch to target
                eventDispatcher.dispatch(event, result.getTarget());
                
                // Audit successful routing
                auditService.logEvent(event, "ROUTED", result.getTarget());
                
                eventsRouted.increment();
                
                log.info("Event routed successfully: type={}, target={}", 
                        event.getType(), result.getTarget());
            } else {
                eventsFailed.increment();
                
                // Audit failed routing
                auditService.logEvent(event, "FAILED", result.getErrorMessage());
                
                log.error("Failed to route event: type={}, error={}", 
                        event.getType(), result.getErrorMessage());
                
                // Optionally send to dead letter queue
                handleFailedEvent(event, result);
            }
            
        } catch (Exception e) {
            eventsFailed.increment();
            
            log.error("Error processing event: type={}, eventId={}", 
                    event.getType(), event.getEventId(), e);
            
            auditService.logEvent(event, "ERROR", e.getMessage());
            
            handleFailedEvent(event, RoutingResult.error(event.getType(), e.getMessage()));
        }
    }
    
    /**
     * Handle events that failed to route
     */
    private void handleFailedEvent(Event event, RoutingResult result) {
        try {
            // Send to dead letter queue for manual review
            eventDispatcher.dispatch(event, "dead-letter-queue");
            log.info("Event sent to dead letter queue: eventId={}", event.getEventId());
        } catch (Exception e) {
            log.error("Failed to send event to dead letter queue: eventId={}", event.getEventId(), e);
        }
    }
}
