package com.neversoft.orchestrator.consumer.sqs;

import com.neversoft.orchestrator.model.Event;
import com.neversoft.orchestrator.service.OrchestratorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import io.awspring.cloud.sqs.annotation.SqsListener;

/**
 * AWS SQS event consumer for production and local development (LocalStack)
 */
@Component
@Profile({"prod", "dev"})
@Slf4j
@RequiredArgsConstructor
public class SQSEventConsumer {
    
    private final OrchestratorService orchestratorService;
    private final ObjectMapper objectMapper;
    
    /**
     * Listen for events on the orchestrator input queue
     */
    @SqsListener("${orchestrator.queue.input:orchestrator-input}")
    public void handleEvent(@Payload String message) {
        try {
            log.debug("Received message from SQS: {}", message);
            
            // Parse event
            Event event = objectMapper.readValue(message, Event.class);
            
            // Process through orchestrator
            orchestratorService.processEvent(event);
            
        } catch (Exception e) {
            log.error("Error handling message from SQS: {}", message, e);
            // In production: send to error queue or DLQ
        }
    }
}
