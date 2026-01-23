package com.neversoft.orchestrator.messaging.sqs;

import com.neversoft.orchestrator.config.MessagingProperties;
import com.neversoft.orchestrator.messaging.MessageBroker;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * AWS SQS implementation of MessageBroker for production and local development (LocalStack)
 */
@Service
@Profile({"prod", "dev"})
@Slf4j
@RequiredArgsConstructor
public class SQSMessageBroker implements MessageBroker {
    
    private static final int SQS_MAX_BATCH_SIZE = 10; // SQS limit
    
    private final SqsTemplate sqsTemplate;
    private final SqsAsyncClient sqsAsyncClient;
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
    
    @Override
    public void sendBatchToQueue(String queueName, List<String> messages, List<Map<String, String>> attributesList) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        
        String queueUrl = resolveQueueUrl(queueName);
        log.debug("Sending batch of {} messages to SQS queue: {}", messages.size(), queueUrl);
        
        // SQS supports up to 10 messages per batch
        // Split into batches of 10 and send asynchronously
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (int i = 0; i < messages.size(); i += SQS_MAX_BATCH_SIZE) {
            int endIndex = Math.min(i + SQS_MAX_BATCH_SIZE, messages.size());
            List<String> batch = messages.subList(i, endIndex);
            List<Map<String, String>> batchAttributes = attributesList != null 
                ? attributesList.subList(i, endIndex) 
                : null;
            
            final int batchIndex = i;
            CompletableFuture<Void> future = sendBatch(queueUrl, batch, batchAttributes, batchIndex)
                .thenRun(() -> log.debug("Batch {} of {} messages sent successfully", batchIndex / SQS_MAX_BATCH_SIZE + 1, batch.size()))
                .exceptionally(ex -> {
                    log.error("Failed to send batch {} to SQS queue: {}", batchIndex / SQS_MAX_BATCH_SIZE + 1, queueName, ex);
                    // Fallback to individual sends for this batch
                    for (int j = 0; j < batch.size(); j++) {
                        try {
                            sendToQueue(queueName, batch.get(j), 
                                batchAttributes != null && j < batchAttributes.size() 
                                    ? batchAttributes.get(j) 
                                    : null);
                        } catch (Exception e) {
                            log.error("Failed to send individual message in batch fallback", e);
                        }
                    }
                    return null;
                });
            
            futures.add(future);
        }
        
        // Wait for all batches to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }
    
    private CompletableFuture<Void> sendBatch(String queueUrl, List<String> messages, List<Map<String, String>> attributesList, int startIndex) {
        List<SendMessageBatchRequestEntry> entries = IntStream.range(0, messages.size())
            .mapToObj(i -> {
                String message = messages.get(i);
                Map<String, String> attributes = attributesList != null && i < attributesList.size() 
                    ? attributesList.get(i) 
                    : null;
                
                SendMessageBatchRequestEntry.Builder entryBuilder = SendMessageBatchRequestEntry.builder()
                    .id(String.valueOf(startIndex + i))
                    .messageBody(message);
                
                // Add message attributes if present
                if (attributes != null && !attributes.isEmpty()) {
                    Map<String, MessageAttributeValue> messageAttributes = attributes.entrySet().stream()
                        .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            e -> MessageAttributeValue.builder()
                                .dataType("String")
                                .stringValue(e.getValue())
                                .build()
                        ));
                    entryBuilder.messageAttributes(messageAttributes);
                }
                
                return entryBuilder.build();
            })
            .collect(Collectors.toList());
        
        SendMessageBatchRequest batchRequest = SendMessageBatchRequest.builder()
            .queueUrl(queueUrl)
            .entries(entries)
            .build();
        
        return sqsAsyncClient.sendMessageBatch(batchRequest)
            .thenApply(response -> {
                if (!response.failed().isEmpty()) {
                    log.warn("Some messages failed in batch: {}", response.failed());
                }
                return null;
            });
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
