package com.neversoft.orchestrator.config;

import com.neversoft.orchestrator.config.model.OrchestrationConfig;
import com.neversoft.orchestrator.model.Event;
import com.neversoft.orchestrator.routing.RoutingEngine;
import com.neversoft.orchestrator.routing.RoutingResult;
import io.awspring.cloud.autoconfigure.config.secretsmanager.SecretsManagerAutoConfiguration;
import io.awspring.cloud.autoconfigure.config.parameterstore.ParameterStoreAutoConfiguration;
import io.awspring.cloud.autoconfigure.core.AwsAutoConfiguration;
import io.awspring.cloud.autoconfigure.core.CredentialsProviderAutoConfiguration;
import io.awspring.cloud.autoconfigure.core.RegionProviderAutoConfiguration;
import io.awspring.cloud.autoconfigure.sqs.SqsAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for multi-orchestration support
 */
@SpringBootTest
@ActiveProfiles("test")
@EnableAutoConfiguration(exclude = {
    SqsAutoConfiguration.class,
    AwsAutoConfiguration.class,
    CredentialsProviderAutoConfiguration.class,
    RegionProviderAutoConfiguration.class,
    SecretsManagerAutoConfiguration.class,
    ParameterStoreAutoConfiguration.class
})
@Import(TestConfig.class)
class MultiOrchestrationIntegrationTest {
    
    @Autowired
    private OrchestrationConfigLoader configLoader;
    
    @Autowired
    private RoutingEngine routingEngine;
    
    @Test
    void testLoadMultipleConfigurations() {
        // Act
        Set<String> orchestrations = configLoader.getLoadedOrchestrations();
        
        // Assert
        assertNotNull(orchestrations);
        assertTrue(orchestrations.size() >= 2);
        assertTrue(orchestrations.contains("order-processing"));
        assertTrue(orchestrations.contains("user-registration"));
    }
    
    @Test
    void testGetOrderProcessingConfig() {
        // Act
        OrchestrationConfig config = configLoader.getConfig("order-processing");
        
        // Assert
        assertNotNull(config);
        assertEquals("Order Processing", config.getName());
        assertFalse(config.getRoutes().isEmpty());
    }
    
    @Test
    void testGetUserRegistrationConfig() {
        // Act
        OrchestrationConfig config = configLoader.getConfig("user-registration");
        
        // Assert
        assertNotNull(config);
        assertEquals("User Registration", config.getName());
        assertFalse(config.getRoutes().isEmpty());
    }
    
    @Test
    void testRoutingWithOrderProcessingOrchestration() {
        // Arrange
        Event event = Event.builder()
                .type("OrderCreated")
                .orchestrationName("order-processing")
                .correlationId("test-order-123")
                .build();
        
        // Act
        RoutingResult result = routingEngine.route(event);
        
        // Assert
        assertTrue(result.isSuccess());
        assertEquals("validation-service-queue", result.getTarget());
    }
    
    @Test
    void testRoutingWithUserRegistrationOrchestration() {
        // Arrange
        Event event = Event.builder()
                .type("UserRegistered")
                .orchestrationName("user-registration")
                .correlationId("test-user-456")
                .build();
        
        // Act
        RoutingResult result = routingEngine.route(event);
        
        // Assert
        assertTrue(result.isSuccess());
        assertEquals("email-verification-queue", result.getTarget());
    }
    
    @Test
    void testRoutingWithDefaultOrchestration() {
        // Arrange - Event without orchestration name should use default
        Event event = Event.builder()
                .type("OrderCreated")
                .orchestrationName(null)
                .correlationId("test-default")
                .build();
        
        // Act
        RoutingResult result = routingEngine.route(event);
        
        // Assert - Should route to order-processing (default)
        assertTrue(result.isSuccess());
        assertEquals("validation-service-queue", result.getTarget());
    }
    
    @Test
    void testRoutingWithUnknownOrchestrationFallsBackToDefault() {
        // Arrange
        Event event = Event.builder()
                .type("OrderCreated")
                .orchestrationName("unknown-orchestration")
                .correlationId("test-unknown")
                .build();
        
        // Act
        RoutingResult result = routingEngine.route(event);
        
        // Assert - Should fall back to default (order-processing)
        assertTrue(result.isSuccess());
        assertEquals("validation-service-queue", result.getTarget());
    }
    
    @Test
    void testConfigValidation() {
        // Act & Assert
        assertTrue(configLoader.isValid());
        assertTrue(configLoader.isValid("order-processing"));
        assertTrue(configLoader.isValid("user-registration"));
        
        // Note: isValid("non-existent") returns true because it falls back to default config
        // To check if a specific orchestration exists, use getLoadedOrchestrations()
        assertTrue(configLoader.isValid("non-existent")); // Falls back to default, which is valid
        assertFalse(configLoader.getLoadedOrchestrations().contains("non-existent"));
    }
}
