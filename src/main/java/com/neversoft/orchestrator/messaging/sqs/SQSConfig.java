package com.neversoft.orchestrator.messaging.sqs;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Configuration for AWS SQS integration (production profile)
 */
@Configuration
@Profile("prod")
@Slf4j
public class SQSConfig {
    
    @Value("${orchestrator.messaging.sqs.region:us-east-1}")
    private String region;
    
    /**
     * Create synchronous SQS client bean
     * Used by QueueInitializer for queue creation
     */
    @Bean
    public SqsClient sqsClient() {
        log.info("Configuring SQS sync client for region: {}", region);
        
        return SqsClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
    
    /**
     * Create SQS async client bean
     * Used by Spring Cloud AWS SQS for message operations
     */
    @Bean
    public SqsAsyncClient sqsAsyncClient() {
        log.info("Configuring SQS async client for region: {}", region);
        
        return SqsAsyncClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
    
    /**
     * Create SqsTemplate bean for sending messages
     */
    @Bean
    public SqsTemplate sqsTemplate(SqsAsyncClient sqsAsyncClient) {
        return SqsTemplate.builder()
                .sqsAsyncClient(sqsAsyncClient)
                .build();
    }
    
    /*
     * Note: defaultSqsListenerContainerFactory is automatically created by 
     * Spring Cloud AWS auto-configuration using our custom sqsAsyncClient bean.
     * No need to define it manually to avoid bean conflicts.
     */
}
