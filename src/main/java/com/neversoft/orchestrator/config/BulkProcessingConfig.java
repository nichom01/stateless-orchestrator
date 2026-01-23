package com.neversoft.orchestrator.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Configuration for bulk event processing with parallel execution
 * Provides a dedicated thread pool executor for processing bulk events in parallel
 */
@Configuration
@Slf4j
public class BulkProcessingConfig {
    
    @Bean(name = "bulkProcessingExecutor")
    public Executor bulkProcessingExecutor(
            @Value("${orchestrator.bulk.processing.threads:100}") int threadPoolSize) {
        log.info("Configuring bulk processing executor with {} threads", threadPoolSize);
        
        return Executors.newFixedThreadPool(threadPoolSize, new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "bulk-processing-" + threadNumber.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        });
    }
}
