package com.neversoft.orchestrator.config.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the complete orchestration configuration loaded from YAML/JSON
 */
@Data
public class OrchestrationConfig {
    
    @NotBlank
    private String name;
    
    private String version;
    
    private String description;
    
    @NotNull
    private List<RouteDefinition> routes = new ArrayList<>();
    
    /**
     * Global settings for the orchestrator
     */
    private OrchestratorSettings settings = new OrchestratorSettings();
    
    @Data
    public static class OrchestratorSettings {
        /**
         * Default queue/topic prefix
         */
        private String queuePrefix = "";
        
        /**
         * Enable audit logging for all events
         */
        private boolean auditEnabled = true;
        
        /**
         * Enable metrics collection
         */
        private boolean metricsEnabled = true;
        
        /**
         * Default timeout for routing operations (milliseconds)
         */
        private long defaultTimeoutMs = 30000;
    }
    
    /**
     * Defines a routing rule: when event type X occurs, route to service Y (with optional conditions)
     */
    @Data
    public static class RouteDefinition {
        
        /**
         * The event type that triggers this route (e.g., "OrderCreated")
         */
        @NotBlank
        private String eventType;
        
        /**
         * Description of what this route does
         */
        private String description;
        
        /**
         * Conditional routing rules. If empty, uses defaultTarget.
         */
        private List<ConditionalRoute> conditions = new ArrayList<>();
        
        /**
         * Default target if no conditions match (or no conditions defined)
         */
        private String defaultTarget;
        
        /**
         * Whether this route is enabled
         */
        private boolean enabled = true;
        
        /**
         * Metadata tags for this route
         */
        private List<String> tags = new ArrayList<>();
    }
    
    /**
     * Represents a conditional routing rule
     */
    @Data
    public static class ConditionalRoute {
        
        /**
         * The condition to evaluate (e.g., "context.customerTier == 'premium'")
         * Uses Spring Expression Language (SpEL)
         */
        @NotBlank
        private String condition;
        
        /**
         * Target queue/service if condition is true
         */
        @NotBlank
        private String target;
        
        /**
         * Description of this conditional route
         */
        private String description;
        
        /**
         * Priority (lower number = higher priority)
         */
        private int priority = 0;
    }
}
