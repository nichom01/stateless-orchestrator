package com.neversoft.orchestrator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for orchestration file loading
 * Supports both legacy single-file mode and new multi-file mode
 */
@Data
@Component
@ConfigurationProperties(prefix = "orchestrator.config")
public class OrchestrationProperties {
    
    /**
     * Legacy single file support (for backward compatibility)
     * If set and files list is empty, system operates in legacy mode
     */
    private String file;
    
    /**
     * New multi-file support - list of orchestration configurations
     */
    private List<OrchestrationFileConfig> files = new ArrayList<>();
    
    /**
     * Default orchestration to use when event doesn't specify one
     */
    private String defaultOrchestration;
    
    /**
     * Configuration for a single orchestration file
     */
    @Data
    public static class OrchestrationFileConfig {
        /**
         * Name identifier for this orchestration (e.g., "order-processing")
         */
        private String name;
        
        /**
         * Path to the orchestration config file (e.g., "classpath:orchestrations/order-processing.yml")
         */
        private String path;
    }
    
    /**
     * Determine if using legacy single-file mode
     * @return true if legacy mode (file is set and files list is empty)
     */
    public boolean isLegacyMode() {
        return file != null && !file.isEmpty() && (files == null || files.isEmpty());
    }
}
