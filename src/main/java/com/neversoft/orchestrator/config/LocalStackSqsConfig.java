package com.neversoft.orchestrator.config;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;

/**
 * Configuration for LocalStack SQS integration (dev profile)
 * Overrides SQS endpoint to point to LocalStack instead of real AWS
 */
@Configuration
@Profile("dev")
@Slf4j
public class LocalStackSqsConfig {
    
    @Value("${orchestrator.messaging.sqs.endpoint:http://localhost:4566}")
    private String localStackEndpoint;
    
    @Value("${orchestrator.messaging.sqs.region:us-east-1}")
    private String region;
    
    /**
     * Create synchronous SQS client configured for LocalStack
     * Used by QueueInitializer for queue creation
     * Uses dummy credentials (LocalStack doesn't validate them)
     */
    @Bean
    public SqsClient sqsClient() {
        log.info("Configuring SQS sync client for LocalStack at endpoint: {}", localStackEndpoint);
        
        return SqsClient.builder()
                .endpointOverride(URI.create(localStackEndpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test", "test")))
                .build();
    }
    
    /**
     * Create async SQS client configured for LocalStack
     * Used by Spring Cloud AWS SQS for message operations
     */
    @Bean
    public SqsAsyncClient sqsAsyncClient() {
        log.info("Configuring SQS async client for LocalStack at endpoint: {}", localStackEndpoint);
        
        return SqsAsyncClient.builder()
                .endpointOverride(URI.create(localStackEndpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test", "test")))
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
