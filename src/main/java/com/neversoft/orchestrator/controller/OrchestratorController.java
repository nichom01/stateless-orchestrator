package com.neversoft.orchestrator.controller;

import com.neversoft.orchestrator.config.OrchestrationConfigLoader;
import com.neversoft.orchestrator.config.model.OrchestrationConfig;
import com.neversoft.orchestrator.model.BulkEventRequest;
import com.neversoft.orchestrator.model.BulkEventResponse;
import com.neversoft.orchestrator.model.Event;
import com.neversoft.orchestrator.routing.RoutingEngine;
import com.neversoft.orchestrator.routing.RoutingResult;
import com.neversoft.orchestrator.service.OrchestratorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

/**
 * REST API for testing and managing the orchestrator
 */
@RestController
@RequestMapping("/api/orchestrator")
@Slf4j
public class OrchestratorController {
    
    private final OrchestratorService orchestratorService;
    private final RoutingEngine routingEngine;
    private final OrchestrationConfigLoader configLoader;
    private final ObjectMapper objectMapper;
    private final Executor bulkProcessingExecutor;
    
    public OrchestratorController(OrchestratorService orchestratorService,
                                  RoutingEngine routingEngine,
                                  OrchestrationConfigLoader configLoader,
                                  ObjectMapper objectMapper,
                                  @Qualifier("bulkProcessingExecutor") Executor bulkProcessingExecutor) {
        this.orchestratorService = orchestratorService;
        this.routingEngine = routingEngine;
        this.configLoader = configLoader;
        this.objectMapper = objectMapper;
        this.bulkProcessingExecutor = bulkProcessingExecutor;
    }
    
    /**
     * Submit an event for processing
     */
    @PostMapping("/events")
    public ResponseEntity<Map<String, Object>> submitEvent(@RequestBody Event event) {
        orchestratorService.processEvent(event);
        
        Map<String, Object> response = new HashMap<>();
        response.put("eventId", event.getEventId());
        response.put("status", "ACCEPTED");
        response.put("correlationId", event.getCorrelationId());
        
        return ResponseEntity.accepted().body(response);
    }
    
    /**
     * Submit multiple events for bulk processing (OPTIMIZED - Parallel Processing)
     * Accepts a JSON array of events or a BulkEventRequest wrapper
     * Processes events in parallel using a dedicated thread pool executor
     */
    @PostMapping("/events/bulk")
    public ResponseEntity<BulkEventResponse> submitBulkEvents(@RequestBody BulkEventRequest request) {
        long startTime = System.currentTimeMillis();
        
        List<Event> events = request.getEvents();
        AtomicInteger successful = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        ConcurrentLinkedQueue<BulkEventResponse.FailedEvent> failures = new ConcurrentLinkedQueue<>();
        
        // Process events in parallel using CompletableFuture
        List<CompletableFuture<Void>> futures = events.stream()
                .map(event -> CompletableFuture.runAsync(() -> {
                    try {
                        orchestratorService.processEvent(event);
                        successful.incrementAndGet();
                    } catch (Exception e) {
                        failed.incrementAndGet();
                        failures.add(BulkEventResponse.FailedEvent.builder()
                                .eventId(event.getEventId())
                                .correlationId(event.getCorrelationId())
                                .type(event.getType())
                                .error(e.getMessage())
                                .build());
                    }
                }, bulkProcessingExecutor))
                .collect(Collectors.toList());
        
        // Wait for all futures to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        long durationMs = System.currentTimeMillis() - startTime;
        
        BulkEventResponse response = BulkEventResponse.builder()
                .total(events.size())
                .successful(successful.get())
                .failed(failed.get())
                .failures(new ArrayList<>(failures))
                .durationMs(durationMs)
                .build();
        
        log.info("Bulk processing completed: {} events in {}ms ({} successful, {} failed)", 
                events.size(), durationMs, successful.get(), failed.get());
        
        return ResponseEntity.accepted().body(response);
    }
    
    /**
     * Alternative bulk endpoint that accepts a JSON array directly
     */
    @PostMapping("/events/bulk-array")
    public ResponseEntity<BulkEventResponse> submitBulkEventsArray(@RequestBody Event[] events) {
        BulkEventRequest request = new BulkEventRequest(java.util.Arrays.asList(events));
        return submitBulkEvents(request);
    }
    
    /**
     * Bulk endpoint that accepts NDJSON (newline-delimited JSON) format
     * Useful for submitting files like test-orders-2500.jsonl
     */
    @PostMapping(value = "/events/bulk-ndjson", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<BulkEventResponse> submitBulkEventsNdjson(@RequestBody String ndjsonContent) {
        List<Event> events = new ArrayList<>();
        String[] lines = ndjsonContent.split("\n");
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            
            try {
                Event event = objectMapper.readValue(line, Event.class);
                events.add(event);
            } catch (Exception e) {
                log.warn("Failed to parse line {} of NDJSON: {}", i + 1, e.getMessage());
                // Continue processing other events even if one fails to parse
            }
        }
        
        if (events.isEmpty()) {
            BulkEventResponse errorResponse = BulkEventResponse.builder()
                    .total(0)
                    .successful(0)
                    .failed(0)
                    .durationMs(0)
                    .build();
            return ResponseEntity.badRequest().body(errorResponse);
        }
        
        BulkEventRequest request = new BulkEventRequest(events);
        return submitBulkEvents(request);
    }
    
    /**
     * Test routing without actually dispatching
     */
    @PostMapping("/events/dry-run")
    public ResponseEntity<RoutingResult> testRouting(@RequestBody Event event) {
        RoutingResult result = routingEngine.route(event);
        return ResponseEntity.ok(result);
    }
    
    /**
     * Get current configuration
     */
    @GetMapping("/config")
    public ResponseEntity<OrchestrationConfig> getConfig() {
        OrchestrationConfig config = configLoader.getConfig();
        return ResponseEntity.ok(config);
    }
    
    /**
     * Reload configuration
     */
    @PostMapping("/config/reload")
    public ResponseEntity<Map<String, Object>> reloadConfig() {
        OrchestrationConfig config = configLoader.reloadConfig();
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "RELOADED");
        response.put("configName", config.getName());
        response.put("version", config.getVersion());
        response.put("routeCount", config.getRoutes().size());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Health check
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("configValid", configLoader.isValid());
        health.put("configName", configLoader.getConfig().getName());
        
        return ResponseEntity.ok(health);
    }
    
    /**
     * List all loaded orchestrations
     */
    @GetMapping("/orchestrations")
    public ResponseEntity<Map<String, Object>> listOrchestrations() {
        Set<String> orchestrations = configLoader.getLoadedOrchestrations();
        
        Map<String, Object> response = new HashMap<>();
        response.put("orchestrations", orchestrations);
        response.put("count", orchestrations.size());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get specific orchestration configuration
     */
    @GetMapping("/orchestrations/{name}")
    public ResponseEntity<OrchestrationConfig> getOrchestrationConfig(@PathVariable String name) {
        OrchestrationConfig config = configLoader.getConfig(name);
        return ResponseEntity.ok(config);
    }
    
    /**
     * Reload specific orchestration configuration
     */
    @PostMapping("/orchestrations/{name}/reload")
    public ResponseEntity<Map<String, Object>> reloadOrchestration(@PathVariable String name) {
        OrchestrationConfig config = configLoader.reloadConfig(name);
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "RELOADED");
        response.put("orchestration", name);
        response.put("version", config.getVersion());
        response.put("routeCount", config.getRoutes().size());
        
        return ResponseEntity.ok(response);
    }
}
