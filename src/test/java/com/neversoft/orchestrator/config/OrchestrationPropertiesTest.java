package com.neversoft.orchestrator.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrchestrationPropertiesTest {
    
    @Test
    void testLegacyModeDetection() {
        // Arrange
        OrchestrationProperties properties = new OrchestrationProperties();
        properties.setFile("classpath:orchestration-config.yml");
        properties.setFiles(new ArrayList<>());
        
        // Act & Assert
        assertTrue(properties.isLegacyMode());
    }
    
    @Test
    void testMultiFileModeDetection() {
        // Arrange
        OrchestrationProperties properties = new OrchestrationProperties();
        properties.setFile(null);
        
        List<OrchestrationProperties.OrchestrationFileConfig> files = new ArrayList<>();
        OrchestrationProperties.OrchestrationFileConfig file1 = new OrchestrationProperties.OrchestrationFileConfig();
        file1.setName("order-processing");
        file1.setPath("classpath:orchestrations/order-processing.yml");
        files.add(file1);
        properties.setFiles(files);
        
        // Act & Assert
        assertFalse(properties.isLegacyMode());
    }
    
    @Test
    void testLegacyModeWithNullFile() {
        // Arrange
        OrchestrationProperties properties = new OrchestrationProperties();
        properties.setFile(null);
        properties.setFiles(new ArrayList<>());
        
        // Act & Assert
        assertFalse(properties.isLegacyMode());
    }
    
    @Test
    void testLegacyModeWithEmptyFile() {
        // Arrange
        OrchestrationProperties properties = new OrchestrationProperties();
        properties.setFile("");
        properties.setFiles(new ArrayList<>());
        
        // Act & Assert
        assertFalse(properties.isLegacyMode());
    }
    
    @Test
    void testOrchestrationFileConfig() {
        // Arrange
        OrchestrationProperties.OrchestrationFileConfig config = new OrchestrationProperties.OrchestrationFileConfig();
        config.setName("test-orchestration");
        config.setPath("classpath:test.yml");
        
        // Act & Assert
        assertEquals("test-orchestration", config.getName());
        assertEquals("classpath:test.yml", config.getPath());
    }
}
