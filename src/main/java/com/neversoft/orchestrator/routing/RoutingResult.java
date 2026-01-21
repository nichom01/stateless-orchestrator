package com.neversoft.orchestrator.routing;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Result of a routing operation
 */
@Data
@AllArgsConstructor
public class RoutingResult {
    
    private String eventType;
    private String target;
    private boolean success;
    private boolean conditionalRoute;
    private String errorMessage;
    
    public static RoutingResult success(String eventType, String target, boolean conditionalRoute) {
        return new RoutingResult(eventType, target, true, conditionalRoute, null);
    }
    
    public static RoutingResult noRoute(String eventType) {
        return new RoutingResult(eventType, null, false, false, "No route found for event type: " + eventType);
    }
    
    public static RoutingResult error(String eventType, String errorMessage) {
        return new RoutingResult(eventType, null, false, false, errorMessage);
    }
}
