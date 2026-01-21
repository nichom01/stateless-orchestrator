package com.neversoft.orchestrator.config;

import com.neversoft.orchestrator.messaging.MessageBroker;
import com.neversoft.orchestrator.model.Event;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Test configuration that provides mock beans for testing
 */
@TestConfiguration
@Profile("test")
public class TestConfig {
    
    /**
     * Mock MessageBroker for testing that doesn't actually send messages
     */
    @Bean
    @Primary
    public MessageBroker testMessageBroker() {
        return new MessageBroker() {
            @Override
            public void sendToQueue(String queueName, String message) {
                // No-op for testing
                System.out.println("Test MessageBroker: Would send message to " + queueName);
            }
            
            @Override
            public void sendToQueue(String queueName, String message, java.util.Map<String, String> attributes) {
                // No-op for testing
                System.out.println("Test MessageBroker: Would send message to " + queueName + " with attributes");
            }
        };
    }
}
