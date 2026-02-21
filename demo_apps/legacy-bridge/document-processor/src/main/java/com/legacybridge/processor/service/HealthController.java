package com.legacybridge.processor.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health check endpoint for the Document Processor service.
 * Returns service status information for monitoring and health checks.
 */
@RestController
public class HealthController {

    private static final Logger logger = LoggerFactory.getLogger(HealthController.class);

    /**
     * Health check endpoint.
     *
     * @return JSON with service status information
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        logger.debug("Health check requested");

        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("service", "document-processor");
        health.put("timestamp", System.currentTimeMillis());
        health.put("jvmFreeMemory", Runtime.getRuntime().freeMemory());
        health.put("jvmTotalMemory", Runtime.getRuntime().totalMemory());

        logger.debug("Health check response: {}", health);
        return health;
    }
}
