package com.neversoft.orchestrator.messaging.sqs;

import com.neversoft.orchestrator.config.MessagingProperties;
import com.neversoft.orchestrator.messaging.MessageBroker;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * AWS SQS implementation of MessageBroker for production and local development (LocalStack)
 */
@Service
@Profile({"prod", "dev"})
@Slf4j
@RequiredArgsConstructor
public class SQSMessageBroker implements MessageBroker {
    
    private final SqsTemplate sqsTemplate;
    private final MessagingProperties messagingProperties;
    
    @Override
    public void sendToQueue(String queueName, String message) {
        sendToQueue(queueName, message, null);
    }
    
    @Override
    public void sendToQueue(String queueName, String message, Map<String, String> attributes) {
        try {
            String queueUrl = resolveQueueUrl(queueName);
            log.debug("Sending message to SQS queue: {} (URL: {})", queueName, queueUrl);
            
            // Send message to SQS with attributes as headers
            // Headers are automatically mapped to SQS message attributes
            sqsTemplate.send(opts -> {
                opts.queue(queueUrl)
                   .payload(message);
                
                // Add message attributes as headers (they'll be mapped to SQS message attributes)
                if (attributes != null) {
                    attributes.forEach(opts::header);
                }
            });
            
            log.debug("Message sent successfully to SQS queue: {}", queueUrl);
            
        } catch (Exception e) {
            log.error("Failed to send message to SQS queue: {}", queueName, e);
            throw new RuntimeException("Failed to send message to SQS queue: " + queueName, e);
        }
    }
    
    /**
     * Resolve queue name to full SQS URL
     * If queueUrlPrefix is configured, prepend it to the queue name
     * Otherwise, assume queue name is already a full URL
     */
    private String resolveQueueUrl(String queueName) {
        String prefix = messagingProperties.getSqs().getQueueUrlPrefix();
        
        if (prefix != null && !prefix.isEmpty()) {
            // Remove trailing slash if present
            String cleanPrefix = prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
            return cleanPrefix + "/" + queueName;
        }
        
        // Assume queue name is already a full URL
        return queueName;
    }
}
