package com.neversoft.orchestrator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for messaging broker selection
 */
@Data
@ConfigurationProperties(prefix = "orchestrator.messaging")
public class MessagingProperties {
    
    /**
     * Message broker type: "sqs"
     */
    private String broker = "sqs";
    
    /**
     * SQS-specific configuration
     */
    private SqsConfig sqs = new SqsConfig();
    
    @Data
    public static class SqsConfig {
        /**
         * AWS region for SQS
         */
        private String region = "us-east-1";
        
        /**
         * Queue URL prefix (e.g., https://sqs.us-east-1.amazonaws.com/123456789012/)
         * If provided, queue names will be prefixed with this to form full URLs
         */
        private String queueUrlPrefix;
    }
}
