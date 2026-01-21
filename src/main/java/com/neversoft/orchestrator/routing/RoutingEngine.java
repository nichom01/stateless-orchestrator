package com.neversoft.orchestrator.routing;

import com.neversoft.orchestrator.config.OrchestrationConfigLoader;
import com.neversoft.orchestrator.config.model.OrchestrationConfig;
import com.neversoft.orchestrator.model.Event;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Core routing engine that evaluates routing rules and determines target services
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RoutingEngine {
    
    private final OrchestrationConfigLoader configLoader;
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    
    /**
     * Route an event to the appropriate target based on configuration
     * 
     * @param event The event to route
     * @return RoutingResult containing target service and metadata
     */
    public RoutingResult route(Event event) {
        log.debug("Routing event: type={}, orchestration={}, correlationId={}", 
                event.getType(), event.getOrchestrationName(), event.getCorrelationId());
        
        // Get the appropriate config based on orchestration name
        OrchestrationConfig config = configLoader.getConfig(event.getOrchestrationName());
        
        // Find matching route definition
        Optional<OrchestrationConfig.RouteDefinition> routeDefOpt = config.getRoutes().stream()
                .filter(r -> r.isEnabled())
                .filter(r -> r.getEventType().equals(event.getType()))
                .findFirst();
        
        if (routeDefOpt.isEmpty()) {
            log.warn("No route definition found for event type: {}", event.getType());
            return RoutingResult.noRoute(event.getType());
        }
        
        OrchestrationConfig.RouteDefinition routeDef = routeDefOpt.get();
        
        // Evaluate conditional routes
        if (!routeDef.getConditions().isEmpty()) {
            Optional<String> target = evaluateConditions(event, routeDef.getConditions());
            if (target.isPresent()) {
                log.debug("Conditional route matched: {} -> {}", event.getType(), target.get());
                return RoutingResult.success(event.getType(), target.get(), true);
            }
        }
        
        // Use default target
        if (routeDef.getDefaultTarget() != null) {
            log.debug("Using default route: {} -> {}", event.getType(), routeDef.getDefaultTarget());
            return RoutingResult.success(event.getType(), routeDef.getDefaultTarget(), false);
        }
        
        log.warn("No target found for event type: {}", event.getType());
        return RoutingResult.noRoute(event.getType());
    }
    
    /**
     * Evaluate conditional routing rules using SpEL
     */
    private Optional<String> evaluateConditions(Event event, List<OrchestrationConfig.ConditionalRoute> conditions) {
        // Sort by priority
        List<OrchestrationConfig.ConditionalRoute> sortedConditions = conditions.stream()
                .sorted(Comparator.comparingInt(OrchestrationConfig.ConditionalRoute::getPriority))
                .toList();
        
        // Create evaluation context with event data
        StandardEvaluationContext context = createEvaluationContext(event);
        
        // Evaluate each condition
        for (OrchestrationConfig.ConditionalRoute condition : sortedConditions) {
            try {
                Boolean result = expressionParser.parseExpression(condition.getCondition())
                        .getValue(context, Boolean.class);
                
                if (Boolean.TRUE.equals(result)) {
                    log.debug("Condition matched: {}", condition.getCondition());
                    return Optional.of(condition.getTarget());
                }
            } catch (Exception e) {
                log.error("Error evaluating condition: {}", condition.getCondition(), e);
            }
        }
        
        return Optional.empty();
    }
    
    /**
     * Create SpEL evaluation context from event
     */
    private StandardEvaluationContext createEvaluationContext(Event event) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        
        // Make event fields available
        context.setVariable("type", event.getType());
        context.setVariable("eventId", event.getEventId());
        context.setVariable("correlationId", event.getCorrelationId());
        context.setVariable("source", event.getSource());
        
        // Make context map available
        context.setVariable("context", event.getContext());
        
        // Make metadata available
        context.setVariable("metadata", event.getMetadata());
        
        // Helper method to access context values directly
        // Allows expressions like: context.customerTier == 'premium'
        context.setRootObject(new ContextWrapper(event.getContext()));
        
        return context;
    }
    
    /**
     * Wrapper to allow direct property access in SpEL expressions
     */
    @SuppressWarnings("unused")
    private static class ContextWrapper {
        private final java.util.Map<String, Object> context;
        
        public ContextWrapper(java.util.Map<String, Object> context) {
            this.context = context;
        }
        
        public java.util.Map<String, Object> getContext() {
            return context;
        }
    }
}
