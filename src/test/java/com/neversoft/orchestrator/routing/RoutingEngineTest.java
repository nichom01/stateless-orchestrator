package com.neversoft.orchestrator.routing;

import com.neversoft.orchestrator.config.OrchestrationConfigLoader;
import com.neversoft.orchestrator.config.model.OrchestrationConfig;
import com.neversoft.orchestrator.model.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoutingEngineTest {
    
    @Mock
    private OrchestrationConfigLoader configLoader;
    
    @InjectMocks
    private RoutingEngine routingEngine;
    
    private OrchestrationConfig testConfig;
    
    private OrchestrationConfig orderConfig;
    private OrchestrationConfig userConfig;
    
    @BeforeEach
    void setUp() {
        testConfig = new OrchestrationConfig();
        testConfig.setName("Test Config");
        testConfig.setRoutes(new ArrayList<>());
        
        orderConfig = new OrchestrationConfig();
        orderConfig.setName("Order Processing");
        orderConfig.setRoutes(new ArrayList<>());
        
        userConfig = new OrchestrationConfig();
        userConfig.setName("User Registration");
        userConfig.setRoutes(new ArrayList<>());
        
        // Use lenient() to avoid UnnecessaryStubbing exceptions
        lenient().when(configLoader.getConfig()).thenReturn(testConfig);
        lenient().when(configLoader.getConfig(null)).thenReturn(testConfig);
    }
    
    @Test
    void testSimpleRouting() {
        // Arrange
        OrchestrationConfig.RouteDefinition route = new OrchestrationConfig.RouteDefinition();
        route.setEventType("OrderCreated");
        route.setDefaultTarget("validation-queue");
        route.setEnabled(true);
        testConfig.getRoutes().add(route);
        
        Event event = Event.builder()
                .type("OrderCreated")
                .correlationId("test-123")
                .build();
        
        // Act
        RoutingResult result = routingEngine.route(event);
        
        // Assert
        assertTrue(result.isSuccess());
        assertEquals("validation-queue", result.getTarget());
        assertFalse(result.isConditionalRoute());
    }
    
    @Test
    void testConditionalRouting() {
        // Arrange
        OrchestrationConfig.RouteDefinition route = new OrchestrationConfig.RouteDefinition();
        route.setEventType("InventoryReserved");
        route.setDefaultTarget("payment-queue");
        route.setEnabled(true);
        
        OrchestrationConfig.ConditionalRoute condition = new OrchestrationConfig.ConditionalRoute();
        condition.setCondition("#context['customerTier'] == 'premium'");
        condition.setTarget("express-payment-queue");
        condition.setPriority(1);
        
        List<OrchestrationConfig.ConditionalRoute> conditions = new ArrayList<>();
        conditions.add(condition);
        route.setConditions(conditions);
        
        testConfig.getRoutes().add(route);
        
        Map<String, Object> context = new HashMap<>();
        context.put("customerTier", "premium");
        
        Event event = Event.builder()
                .type("InventoryReserved")
                .correlationId("test-456")
                .context(context)
                .build();
        
        // Act
        RoutingResult result = routingEngine.route(event);
        
        // Assert
        assertTrue(result.isSuccess());
        assertEquals("express-payment-queue", result.getTarget());
        assertTrue(result.isConditionalRoute());
    }
    
    @Test
    void testNoRouteFound() {
        // Arrange
        Event event = Event.builder()
                .type("UnknownEvent")
                .correlationId("test-789")
                .build();
        
        // Act
        RoutingResult result = routingEngine.route(event);
        
        // Assert
        assertFalse(result.isSuccess());
        assertNull(result.getTarget());
        assertNotNull(result.getErrorMessage());
    }
    
    @Test
    void testDisabledRoute() {
        // Arrange
        OrchestrationConfig.RouteDefinition route = new OrchestrationConfig.RouteDefinition();
        route.setEventType("OrderCreated");
        route.setDefaultTarget("validation-queue");
        route.setEnabled(false);  // Disabled
        testConfig.getRoutes().add(route);
        
        Event event = Event.builder()
                .type("OrderCreated")
                .correlationId("test-999")
                .build();
        
        // Act
        RoutingResult result = routingEngine.route(event);
        
        // Assert
        assertFalse(result.isSuccess());
    }
    
    @Test
    void testRoutingWithExplicitOrchestrationName() {
        // Arrange
        OrchestrationConfig.RouteDefinition route = new OrchestrationConfig.RouteDefinition();
        route.setEventType("OrderCreated");
        route.setDefaultTarget("validation-queue");
        route.setEnabled(true);
        orderConfig.getRoutes().add(route);
        
        lenient().when(configLoader.getConfig("order-processing")).thenReturn(orderConfig);
        
        Event event = Event.builder()
                .type("OrderCreated")
                .orchestrationName("order-processing")
                .correlationId("test-explicit")
                .build();
        
        // Act
        RoutingResult result = routingEngine.route(event);
        
        // Assert
        assertTrue(result.isSuccess());
        assertEquals("validation-queue", result.getTarget());
    }
    
    @Test
    void testRoutingWithDefaultOrchestration() {
        // Arrange
        OrchestrationConfig.RouteDefinition route = new OrchestrationConfig.RouteDefinition();
        route.setEventType("OrderCreated");
        route.setDefaultTarget("validation-queue");
        route.setEnabled(true);
        testConfig.getRoutes().add(route);
        
        Event event = Event.builder()
                .type("OrderCreated")
                .orchestrationName(null)  // No orchestration specified
                .correlationId("test-default")
                .build();
        
        // Act
        RoutingResult result = routingEngine.route(event);
        
        // Assert
        assertTrue(result.isSuccess());
        assertEquals("validation-queue", result.getTarget());
    }
    
    @Test
    void testRoutingWithUnknownOrchestration() {
        // Arrange
        OrchestrationConfig.RouteDefinition route = new OrchestrationConfig.RouteDefinition();
        route.setEventType("OrderCreated");
        route.setDefaultTarget("validation-queue");
        route.setEnabled(true);
        testConfig.getRoutes().add(route);
        
        // Mock: unknown orchestration falls back to default
        lenient().when(configLoader.getConfig("unknown-orchestration")).thenReturn(testConfig);
        
        Event event = Event.builder()
                .type("OrderCreated")
                .orchestrationName("unknown-orchestration")
                .correlationId("test-unknown")
                .build();
        
        // Act
        RoutingResult result = routingEngine.route(event);
        
        // Assert
        assertTrue(result.isSuccess());
        assertEquals("validation-queue", result.getTarget());
    }
    
    @Test
    void testRoutingWithDifferentOrchestrations() {
        // Arrange - Order Processing orchestration
        OrchestrationConfig.RouteDefinition orderRoute = new OrchestrationConfig.RouteDefinition();
        orderRoute.setEventType("OrderCreated");
        orderRoute.setDefaultTarget("order-validation-queue");
        orderRoute.setEnabled(true);
        orderConfig.getRoutes().add(orderRoute);
        
        // Arrange - User Registration orchestration
        OrchestrationConfig.RouteDefinition userRoute = new OrchestrationConfig.RouteDefinition();
        userRoute.setEventType("UserRegistered");
        userRoute.setDefaultTarget("email-verification-queue");
        userRoute.setEnabled(true);
        userConfig.getRoutes().add(userRoute);
        
        lenient().when(configLoader.getConfig("order-processing")).thenReturn(orderConfig);
        lenient().when(configLoader.getConfig("user-registration")).thenReturn(userConfig);
        
        // Act - Route order event
        Event orderEvent = Event.builder()
                .type("OrderCreated")
                .orchestrationName("order-processing")
                .correlationId("order-123")
                .build();
        RoutingResult orderResult = routingEngine.route(orderEvent);
        
        // Act - Route user event
        Event userEvent = Event.builder()
                .type("UserRegistered")
                .orchestrationName("user-registration")
                .correlationId("user-456")
                .build();
        RoutingResult userResult = routingEngine.route(userEvent);
        
        // Assert
        assertTrue(orderResult.isSuccess());
        assertEquals("order-validation-queue", orderResult.getTarget());
        
        assertTrue(userResult.isSuccess());
        assertEquals("email-verification-queue", userResult.getTarget());
    }
}
