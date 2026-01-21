---
name: Multi-Orchestration Support
overview: Add support for multiple orchestration configurations loaded from separate files, with events explicitly specifying which orchestration to use via an orchestrationName field.
todos:
  - id: config-properties
    content: Create OrchestrationProperties class for multi-file configuration support
    status: pending
  - id: event-model
    content: Add orchestrationName field to Event model
    status: pending
  - id: config-loader
    content: Refactor OrchestrationConfigLoader to load and cache multiple configs by name
    status: pending
  - id: routing-engine
    content: Update RoutingEngine to get config by orchestration name from event
    status: pending
  - id: orchestrator-service
    content: Update OrchestratorService logging to include orchestration name
    status: pending
  - id: audit-service
    content: Update AuditService to log orchestration name
    status: pending
  - id: controller-endpoints
    content: Add new controller endpoints for listing and managing orchestrations
    status: pending
  - id: unit-tests
    content: Update and add unit tests for multi-orchestration routing
    status: pending
  - id: integration-tests
    content: Create integration tests for multi-orchestration scenarios
    status: pending
  - id: example-configs
    content: Create example orchestration config files (order-processing.yml, user-registration.yml)
    status: pending
  - id: application-yml
    content: Update application.yml with multi-orchestration configuration example
    status: pending
  - id: documentation
    content: Update README.md, QUICKSTART.md, and create MIGRATION_GUIDE.md
    status: pending
---

# Multi-Orchestration Configuration Support

## Overview

Implement Approach 1: **Explicit Orchestration Name** - Events will carry an `orchestrationName` field that specifies which orchestration configuration to use for routing. The system will load multiple orchestration config files at startup and select the appropriate one based on the event's orchestration name.

## Configuration Structure

### New Configuration Format

Update [`application.yml`](src/main/resources/application.yml) to support multiple orchestration files:

```yaml
orchestrator:
  config:
    # NEW: List of orchestration configurations
    files:
      - name: "order-processing"
        path: "classpath:orchestrations/order-processing.yml"
      - name: "user-registration"
        path: "classpath:orchestrations/user-registration.yml"
    # Optional: Default orchestration when none specified
    defaultOrchestration: "order-processing"
    
    # DEPRECATED but still supported for backward compatibility
    # file: classpath:orchestration-config.yml
```

### Directory Structure

Create new directory structure:
```
src/main/resources/
  ├── orchestrations/
  │   ├── order-processing.yml
  │   ├── user-registration.yml
  │   └── shipping.yml
  └── orchestration-config.yml (legacy, kept for backward compatibility)
```

## Implementation Changes

### 1. Configuration Properties

**Create new class:** `OrchestrationProperties.java`

Add to [`MessagingProperties.java`](src/main/java/com/example/orchestrator/config/MessagingProperties.java) package:

```java
@Data
@ConfigurationProperties(prefix = "orchestrator.config")
public class OrchestrationProperties {
    
    // Legacy single file support
    private String file;
    
    // New multi-file support
    private List<OrchestrationFileConfig> files = new ArrayList<>();
    
    // Default orchestration to use when event doesn't specify one
    private String defaultOrchestration;
    
    @Data
    public static class OrchestrationFileConfig {
        private String name;
        private String path;
    }
    
    // Helper to determine if using legacy single-file mode
    public boolean isLegacyMode() {
        return file != null && !file.isEmpty() && files.isEmpty();
    }
}
```

### 2. Event Model Updates

Update [`Event.java`](src/main/java/com/example/orchestrator/model/Event.java):

Add new field at line 52 (after `correlationId`):
```java
/**
 * Name of the orchestration configuration to use for routing this event.
 * If null, the default orchestration will be used.
 */
private String orchestrationName;
```

Update builder to include this field - Lombok will handle this automatically.

### 3. OrchestrationConfigLoader Refactoring

**Major changes to** [`OrchestrationConfigLoader.java`](src/main/java/com/example/orchestrator/config/OrchestrationConfigLoader.java):

#### Add new fields:
```java
@Autowired
private OrchestrationProperties orchestrationProperties;

private Map<String, OrchestrationConfig> configCache = new ConcurrentHashMap<>();
```

#### Update `init()` method (line 34-45):
```java
@PostConstruct
public void init() {
    try {
        if (orchestrationProperties.isLegacyMode()) {
            // Legacy: Load single config file
            OrchestrationConfig config = loadConfigFromResource(configFile);
            String name = config.getName();
            configCache.put(name, config);
            log.info("Loaded orchestration config (legacy mode): {}", name);
        } else {
            // New: Load multiple config files
            for (OrchestrationProperties.OrchestrationFileConfig fileConfig : orchestrationProperties.getFiles()) {
                Resource resource = resourceLoader.getResource(fileConfig.getPath());
                OrchestrationConfig config = loadConfigFromResource(resource);
                configCache.put(fileConfig.getName(), config);
                log.info("Loaded orchestration config: {} from {}", fileConfig.getName(), fileConfig.getPath());
            }
            log.info("Successfully loaded {} orchestration configurations", configCache.size());
        }
    } catch (Exception e) {
        log.error("Failed to load orchestration configuration", e);
        throw new RuntimeException("Failed to load orchestration configuration", e);
    }
}
```

#### Add new methods:
```java
/**
 * Get configuration by orchestration name
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
 */
private OrchestrationConfig getDefaultConfig() {
    String defaultName = orchestrationProperties.getDefaultOrchestration();
    
    if (defaultName != null && configCache.containsKey(defaultName)) {
        return configCache.get(defaultName);
    }
    
    // If no default specified, return first available config
    if (!configCache.isEmpty()) {
        return configCache.values().iterator().next();
    }
    
    throw new IllegalStateException("No orchestration configurations loaded");
}

/**
 * Get all loaded orchestration names
 */
public Set<String> getLoadedOrchestrations() {
    return Collections.unmodifiableSet(configCache.keySet());
}

/**
 * Reload a specific orchestration configuration
 */
public OrchestrationConfig reloadConfig(String orchestrationName) {
    // Implementation for selective reload
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
            return yamlMapper.readValue(inputStream, OrchestrationConfig.class);
        }
    }
}
```

#### Keep backward compatibility:
Maintain existing `getConfig()` method for legacy callers:
```java
@Cacheable("orchestrationConfig")
public OrchestrationConfig getConfig() {
    return getDefaultConfig();
}
```

### 4. RoutingEngine Updates

Update [`RoutingEngine.java`](src/main/java/com/example/orchestrator/routing/RoutingEngine.java):

#### Modify `route()` method (line 34-69):
```java
public RoutingResult route(Event event) {
    log.debug("Routing event: type={}, orchestration={}, correlationId={}", 
            event.getType(), event.getOrchestrationName(), event.getCorrelationId());
    
    // Get the appropriate config based on orchestration name
    OrchestrationConfig config = configLoader.getConfig(event.getOrchestrationName());
    
    // ... rest of routing logic remains the same
}
```

### 5. OrchestratorService Updates

Update [`OrchestratorService.java`](src/main/java/com/example/orchestrator/service/OrchestratorService.java):

#### Update logging in `processEvent()` (line 60-61):
```java
log.info("Processing event: type={}, orchestration={}, eventId={}, correlationId={}", 
        event.getType(), event.getOrchestrationName(), event.getEventId(), event.getCorrelationId());
```

No other changes needed - the orchestration name flows through automatically.

### 6. Controller Updates

Update [`OrchestratorController.java`](src/main/java/com/example/orchestrator/controller/OrchestratorController.java):

#### Add new endpoint to list orchestrations (after line 88):
```java
/**
 * List all loaded orchestrations
 */
@GetMapping("/orchestrations")
public ResponseEntity<Map<String, Object>> listOrchestrations() {
    Set<String> orchestrations = configLoader.getLoadedOrchestrations();
    
    Map<String, Object> response = new HashMap<>();
    response.put("orchestrations", orchestrations);
    response.put("count", orchestrations.size());
    
    return ResponseEntity.ok(response);
}

/**
 * Get specific orchestration configuration
 */
@GetMapping("/orchestrations/{name}")
public ResponseEntity<OrchestrationConfig> getOrchestrationConfig(@PathVariable String name) {
    OrchestrationConfig config = configLoader.getConfig(name);
    return ResponseEntity.ok(config);
}

/**
 * Reload specific orchestration configuration
 */
@PostMapping("/orchestrations/{name}/reload")
public ResponseEntity<Map<String, Object>> reloadOrchestration(@PathVariable String name) {
    OrchestrationConfig config = configLoader.reloadConfig(name);
    
    Map<String, Object> response = new HashMap<>();
    response.put("status", "RELOADED");
    response.put("orchestration", name);
    response.put("version", config.getVersion());
    response.put("routeCount", config.getRoutes().size());
    
    return ResponseEntity.ok(response);
}
```

### 7. AuditService Enhancement

Update [`AuditService.java`](src/main/java/com/example/orchestrator/service/AuditService.java):

#### Update log format (line 42-47):
Include orchestration name in audit logs:
```java
log.info("AUDIT | EventId: {} | Type: {} | Orchestration: {} | CorrelationId: {} | Status: {} | Details: {} | Timestamp: {}", 
        event.getEventId(),
        event.getType(),
        event.getOrchestrationName(),
        event.getCorrelationId(),
        status,
        details,
        Instant.now());
```

## Testing Strategy

### 1. Unit Tests

Update [`RoutingEngineTest.java`](src/test/java/com/example/orchestrator/routing/RoutingEngineTest.java):

Add tests for multi-orchestration scenarios:
```java
@Test
void testRoutingWithExplicitOrchestrationName() {
    // Test routing when orchestration name is specified
}

@Test
void testRoutingWithDefaultOrchestration() {
    // Test routing when orchestration name is null
}

@Test
void testRoutingWithUnknownOrchestration() {
    // Test fallback to default when orchestration name not found
}
```

### 2. Integration Tests

Create new test class: `MultiOrchestrationIntegrationTest.java`
- Test loading multiple config files
- Test event routing to different orchestrations
- Test selective reload
- Test backward compatibility with single file

### 3. Configuration Tests

Create: `OrchestrationPropertiesTest.java`
- Test legacy mode detection
- Test multi-file configuration parsing
- Test default orchestration selection

## Example Orchestration Files

### order-processing.yml
```yaml
name: "Order Processing"
version: "1.0.0"
description: "Routes events for e-commerce order processing"

routes:
  - eventType: "OrderCreated"
    defaultTarget: "validation-service-queue"
  
  - eventType: "OrderValidated"
    defaultTarget: "inventory-service-queue"
```

### user-registration.yml
```yaml
name: "User Registration"
version: "1.0.0"
description: "Routes events for user registration workflow"

routes:
  - eventType: "UserRegistered"
    defaultTarget: "email-verification-queue"
  
  - eventType: "EmailVerified"
    defaultTarget: "profile-setup-queue"
```

## Example Event Payloads

### With explicit orchestration:
```json
{
  "type": "OrderCreated",
  "orchestrationName": "order-processing",
  "correlationId": "order-12345",
  "context": {
    "orderId": "ORD-001",
    "customerId": "CUST-456"
  }
}
```

### Using default orchestration:
```json
{
  "type": "UserRegistered",
  "orchestrationName": null,
  "correlationId": "user-789",
  "context": {
    "userId": "USER-001",
    "email": "user@example.com"
  }
}
```

## Backward Compatibility

The implementation maintains full backward compatibility:

1. **Legacy single-file mode**: If `orchestrator.config.file` is set and `orchestrator.config.files` is empty, the system operates in legacy mode
2. **Events without orchestrationName**: Events with null/empty orchestration name use the default orchestration
3. **Existing API endpoints**: All existing endpoints continue to work with the default orchestration
4. **Gradual migration path**: Teams can migrate one orchestration at a time

## Documentation Updates

### Update README.md
- Add section on "Multiple Orchestrations"
- Document new configuration format
- Add examples of multi-orchestration setup
- Update API documentation with new endpoints

### Update QUICKSTART.md
- Add quick example of multi-orchestration setup
- Show how to specify orchestration name in events

### Create MIGRATION_GUIDE.md
- Document migration from single to multiple orchestrations
- Provide step-by-step instructions
- Include rollback procedures

## Rollout Strategy

### Phase 1: Code Implementation
1. Add OrchestrationProperties configuration class
2. Update Event model with orchestrationName field
3. Refactor OrchestrationConfigLoader for multi-file support
4. Update RoutingEngine, OrchestratorService, AuditService
5. Add new Controller endpoints

### Phase 2: Testing
1. Write unit tests for all modified components
2. Create integration tests for multi-orchestration scenarios
3. Test backward compatibility with legacy single-file mode
4. Performance testing with multiple loaded configs

### Phase 3: Documentation
1. Update README and QUICKSTART guides
2. Create migration guide
3. Update API documentation
4. Add inline code documentation

### Phase 4: Deployment
1. Deploy with backward-compatible default configuration
2. Monitor logs for any issues
3. Gradually migrate orchestrations to separate files
4. Validate routing behavior in production