package com.legacybridge.api.resource;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.Map;

/**
 * JAX-RS resource providing a health check endpoint for the REST API.
 * Returns the service status, name, and current timestamp.
 */
@Path("/health")
@Produces(MediaType.APPLICATION_JSON)
public class HealthResource {

    /**
     * GET /health - Returns the health status of the REST API service.
     */
    @GET
    public Map<String, Object> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "rest-api");
        health.put("timestamp", System.currentTimeMillis());
        return health;
    }
}
