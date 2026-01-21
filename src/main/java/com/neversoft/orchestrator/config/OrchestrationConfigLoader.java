package com.neversoft.orchestrator.config;

import com.neversoft.orchestrator.config.model.OrchestrationConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads and caches orchestration configuration from YAML or JSON files.
 * Supports both legacy single-file mode and new multi-file mode.
 */
@Service
@Slf4j
public class OrchestrationConfigLoader {
    
    @Value("${orchestrator.config.file:classpath:orchestration-config.yml}")
    private Resource configFile;
    
    @Autowired
    private OrchestrationProperties orchestrationProperties;
    
    @Autowired
    private ResourceLoader resourceLoader;
    
    // Legacy single config (for backward compatibility)
    private OrchestrationConfig cachedConfig;
    
    // New multi-config cache (keyed by orchestration name)
    private Map<String, OrchestrationConfig> configCache = new ConcurrentHashMap<>();
    
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final ObjectMapper jsonMapper = new ObjectMapper();
    
    /**
     * Load configuration on startup
     */
    @PostConstruct
    public void init() {
        try {
            if (orchestrationProperties.isLegacyMode()) {
                // Legacy: Load single config file
                OrchestrationConfig config = loadConfigFromResource(configFile);
                String name = config.getName() != null ? config.getName() : "default";
                configCache.put(name, config);
                this.cachedConfig = config; // Keep for backward compatibility
                log.info("Loaded orchestration config (legacy mode): {} (version: {})", 
                        name, config.getVersion());
                log.info("Loaded {} route definitions", config.getRoutes().size());
            } else {
                // New: Load multiple config files
                if (orchestrationProperties.getFiles() == null || orchestrationProperties.getFiles().isEmpty()) {
                    log.warn("No orchestration files configured. Falling back to legacy mode.");
                    OrchestrationConfig config = loadConfigFromResource(configFile);
                    String name = config.getName() != null ? config.getName() : "default";
                    configCache.put(name, config);
                    this.cachedConfig = config;
                    log.info("Loaded orchestration config (fallback to legacy): {} (version: {})", 
                            name, config.getVersion());
                } else {
                    for (OrchestrationProperties.OrchestrationFileConfig fileConfig : orchestrationProperties.getFiles()) {
                        try {
                            Resource resource = resourceLoader.getResource(fileConfig.getPath());
                            OrchestrationConfig config = loadConfigFromResource(resource);
                            configCache.put(fileConfig.getName(), config);
                            log.info("Loaded orchestration config: {} from {} (version: {}, {} routes)", 
                                    fileConfig.getName(), fileConfig.getPath(), 
                                    config.getVersion(), config.getRoutes().size());
                        } catch (Exception e) {
                            log.error("Failed to load orchestration config: {} from {}", 
                                    fileConfig.getName(), fileConfig.getPath(), e);
                            throw new RuntimeException("Failed to load orchestration config: " + fileConfig.getName(), e);
                        }
                    }
                    log.info("Successfully loaded {} orchestration configurations", configCache.size());
                    // Set first config as cachedConfig for backward compatibility
                    if (!configCache.isEmpty()) {
                        this.cachedConfig = configCache.values().iterator().next();
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to load orchestration configuration", e);
            throw new RuntimeException("Failed to load orchestration configuration", e);
        }
    }
    
    /**
     * Get the cached configuration (backward compatibility - returns default config)
     */
    @Cacheable("orchestrationConfig")
    public OrchestrationConfig getConfig() {
        return getDefaultConfig();
    }
    
    /**
     * Get configuration by orchestration name
     * @param orchestrationName The name of the orchestration to retrieve
     * @return The orchestration configuration, or default if not found
     */
    public OrchestrationConfig getConfig(String orchestrationName) {
        if (orchestrationName == null || orchestrationName.isEmpty()) {
            return getDefaultConfig();
        }
        
        OrchestrationConfig config = configCache.get(orchestrationName);
        if (config == null) {
            log.warn("No configuration found for orchestration: {}, using default", orchestrationName);
            return getDefaultConfig();
        }
        return config;
    }
    
    /**
     * Get default configuration
     * @return The default orchestration configuration
     */
    private OrchestrationConfig getDefaultConfig() {
        // If using legacy mode, return cached config
        if (cachedConfig != null && orchestrationProperties != null && orchestrationProperties.isLegacyMode()) {
            return cachedConfig;
        }
        
        // Check if default orchestration is specified
        if (orchestrationProperties != null && orchestrationProperties.getDefaultOrchestration() != null) {
            String defaultName = orchestrationProperties.getDefaultOrchestration();
            if (configCache.containsKey(defaultName)) {
                return configCache.get(defaultName);
            }
        }
        
        // If no default specified, return first available config
        if (!configCache.isEmpty()) {
            return configCache.values().iterator().next();
        }
        
        // Fallback to legacy cached config if available
        if (cachedConfig != null) {
            return cachedConfig;
        }
        
        throw new IllegalStateException("No orchestration configurations loaded");
    }
    
    /**
     * Get all loaded orchestration names
     * @return Set of orchestration names
     */
    public Set<String> getLoadedOrchestrations() {
        return Collections.unmodifiableSet(configCache.keySet());
    }
    
    /**
     * Reload configuration from file (backward compatibility - reloads default)
     */
    public OrchestrationConfig reloadConfig() {
        if (orchestrationProperties != null && orchestrationProperties.isLegacyMode()) {
            try {
                this.cachedConfig = loadConfigFromResource(configFile);
                String name = cachedConfig.getName() != null ? cachedConfig.getName() : "default";
                configCache.put(name, cachedConfig);
                log.info("Successfully reloaded orchestration config (legacy mode)");
                return cachedConfig;
            } catch (IOException e) {
                log.error("Failed to reload orchestration configuration", e);
                throw new RuntimeException("Failed to reload orchestration configuration", e);
            }
        } else {
            // Reload default orchestration
            String defaultName = orchestrationProperties != null && orchestrationProperties.getDefaultOrchestration() != null
                    ? orchestrationProperties.getDefaultOrchestration()
                    : (!configCache.isEmpty() ? configCache.keySet().iterator().next() : null);
            
            if (defaultName == null) {
                throw new IllegalStateException("No orchestration configurations available to reload");
            }
            
            return reloadConfig(defaultName);
        }
    }
    
    /**
     * Reload a specific orchestration configuration
     * @param orchestrationName The name of the orchestration to reload
     * @return The reloaded orchestration configuration
     */
    public OrchestrationConfig reloadConfig(String orchestrationName) {
        if (orchestrationProperties == null || orchestrationProperties.isLegacyMode()) {
            // Legacy mode - reload single file
            try {
                OrchestrationConfig config = loadConfigFromResource(configFile);
                String name = config.getName() != null ? config.getName() : "default";
                configCache.put(name, config);
                this.cachedConfig = config;
                log.info("Successfully reloaded orchestration config: {}", name);
                return config;
            } catch (IOException e) {
                log.error("Failed to reload orchestration configuration", e);
                throw new RuntimeException("Failed to reload orchestration configuration", e);
            }
        }
        
        // Find the file config for this orchestration
        OrchestrationProperties.OrchestrationFileConfig fileConfig = orchestrationProperties.getFiles().stream()
                .filter(f -> f.getName().equals(orchestrationName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Orchestration not found: " + orchestrationName));
        
        try {
            Resource resource = resourceLoader.getResource(fileConfig.getPath());
            OrchestrationConfig config = loadConfigFromResource(resource);
            configCache.put(orchestrationName, config);
            log.info("Successfully reloaded orchestration config: {} from {}", 
                    orchestrationName, fileConfig.getPath());
            return config;
        } catch (IOException e) {
            log.error("Failed to reload orchestration configuration: {}", orchestrationName, e);
            throw new RuntimeException("Failed to reload orchestration configuration: " + orchestrationName, e);
        }
    }
    
    /**
     * Extract loading logic to separate method for reuse
     */
    private OrchestrationConfig loadConfigFromResource(Resource resource) throws IOException {
        String filename = resource.getFilename();
        log.debug("Loading orchestration config from: {}", filename);
        
        try (InputStream inputStream = resource.getInputStream()) {
            if (filename != null && (filename.endsWith(".yml") || filename.endsWith(".yaml"))) {
                return yamlMapper.readValue(inputStream, OrchestrationConfig.class);
            } else if (filename != null && filename.endsWith(".json")) {
                return jsonMapper.readValue(inputStream, OrchestrationConfig.class);
            } else {
                // Default to YAML
                return yamlMapper.readValue(inputStream, OrchestrationConfig.class);
            }
        }
    }
    
    /**
     * Validate configuration (validates default config)
     */
    public boolean isValid() {
        try {
            OrchestrationConfig config = getConfig();
            return config != null 
                    && config.getName() != null 
                    && !config.getRoutes().isEmpty();
        } catch (Exception e) {
            log.error("Configuration validation failed", e);
            return false;
        }
    }
    
    /**
     * Validate a specific orchestration configuration
     * @param orchestrationName The name of the orchestration to validate
     * @return true if valid, false otherwise
     */
    public boolean isValid(String orchestrationName) {
        try {
            OrchestrationConfig config = getConfig(orchestrationName);
            return config != null 
                    && config.getName() != null 
                    && !config.getRoutes().isEmpty();
        } catch (Exception e) {
            log.error("Configuration validation failed for orchestration: {}", orchestrationName, e);
            return false;
        }
    }
}
