package com.neversoft.orchestrator.config;

import com.neversoft.orchestrator.config.model.OrchestrationConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import jakarta.annotation.PostConstruct;
import java.util.HashSet;
import java.util.Set;

/**
 * Automatically creates required SQS queues on application startup.
 * Extracts queue names from orchestration configuration and creates them if they don't exist.
 * Works with both LocalStack (dev) and real AWS SQS (prod).
 */
@Component
@Profile({"dev", "prod"})
@Slf4j
@RequiredArgsConstructor
public class QueueInitializer {
    
    private final OrchestrationConfigLoader configLoader;
    private final SqsClient sqsClient;
    
    @Value("${orchestrator.queue.input:orchestrator-input}")
    private String inputQueueName;
    
    /**
     * Initialize queues on application startup
     */
    @PostConstruct
    public void initializeQueues() {
        try {
            OrchestrationConfig config = configLoader.getConfig();
            Set<String> queueNames = extractQueueNames(config);
            
            // Add input queue
            queueNames.add(inputQueueName);
            
            log.info("Initializing {} queues from orchestration configuration", queueNames.size());
            
            int created = 0;
            int existing = 0;
            
            for (String queueName : queueNames) {
                if (createQueueIfNotExists(queueName)) {
                    created++;
                } else {
                    existing++;
                }
            }
            
            log.info("Queue initialization complete: {} created, {} already existed", created, existing);
            
        } catch (Exception e) {
            log.error("Failed to initialize queues", e);
            // Don't throw - allow application to start even if queue creation fails
            // Queues might already exist or be created manually
        }
    }
    
    /**
     * Extract all unique queue names from orchestration configuration
     * Note: Queue prefix from settings is not applied here as it's not used in routing/dispatching logic.
     * If you need prefixed queues, add the prefix directly to queue names in the config.
     */
    private Set<String> extractQueueNames(OrchestrationConfig config) {
        Set<String> queueNames = new HashSet<>();
        
        for (OrchestrationConfig.RouteDefinition route : config.getRoutes()) {
            if (!route.isEnabled()) {
                continue;
            }
            
            // Add default target if present
            if (route.getDefaultTarget() != null && !route.getDefaultTarget().isEmpty()) {
                queueNames.add(route.getDefaultTarget());
            }
            
            // Add conditional targets
            if (route.getConditions() != null) {
                for (OrchestrationConfig.ConditionalRoute condition : route.getConditions()) {
                    if (condition.getTarget() != null && !condition.getTarget().isEmpty()) {
                        queueNames.add(condition.getTarget());
                    }
                }
            }
        }
        
        return queueNames;
    }
    
    /**
     * Create queue if it doesn't exist. Returns true if created, false if already exists.
     */
    private boolean createQueueIfNotExists(String queueName) {
        try {
            // Check if queue already exists by trying to get its URL
            try {
                GetQueueUrlRequest getQueueUrlRequest = GetQueueUrlRequest.builder()
                        .queueName(queueName)
                        .build();
                
                sqsClient.getQueueUrl(getQueueUrlRequest);
                log.debug("Queue already exists: {}", queueName);
                return false;
                
            } catch (QueueDoesNotExistException e) {
                // Queue doesn't exist, create it
                CreateQueueRequest createRequest = CreateQueueRequest.builder()
                        .queueName(queueName)
                        .build();
                
                CreateQueueResponse response = sqsClient.createQueue(createRequest);
                log.info("Created queue: {} (URL: {})", queueName, response.queueUrl());
                return true;
            }
            
        } catch (QueueNameExistsException e) {
            log.debug("Queue already exists (race condition): {}", queueName);
            return false;
            
        } catch (Exception e) {
            log.error("Failed to create queue: {}", queueName, e);
            // Don't throw - continue with other queues
            return false;
        }
    }
}
