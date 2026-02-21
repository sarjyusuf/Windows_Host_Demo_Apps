package com.legacybridge.api.config;

import com.legacybridge.api.service.DocumentStore;
import com.legacybridge.api.service.JmsService;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.ApplicationPath;

/**
 * JAX-RS Application configuration for the LegacyBridge REST API.
 * Extends Jersey ResourceConfig to register providers, features, and component packages.
 * Initializes the H2 document store and ActiveMQ JMS service on startup.
 */
@ApplicationPath("/api")
public class RestApiApplication extends ResourceConfig {

    private static final Logger logger = LoggerFactory.getLogger(RestApiApplication.class);

    public RestApiApplication() {
        logger.info("Initializing LegacyBridge REST API Application");

        // Register JAX-RS resource packages
        packages("com.legacybridge.api.resource");

        // Register Jackson JSON provider
        register(JacksonFeature.class);

        // Register Multipart support for file uploads
        register(MultiPartFeature.class);

        // Initialize singleton services
        logger.info("Initializing DocumentStore (H2 database)");
        DocumentStore documentStore = DocumentStore.getInstance();
        documentStore.initialize();

        logger.info("Initializing JMS Service (ActiveMQ)");
        JmsService jmsService = JmsService.getInstance();

        // Store service references as properties for resource access
        property("documentStore", documentStore);
        property("jmsService", jmsService);

        logger.info("REST API Application initialized successfully");
    }
}
