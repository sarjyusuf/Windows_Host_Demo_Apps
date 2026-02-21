package com.legacybridge.tika;

import com.legacybridge.tika.parser.DocumentParser;
import com.legacybridge.tika.server.HealthHandler;
import com.legacybridge.tika.server.ParseHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * Tika Processor Application - Standalone JAR with embedded HTTP server.
 * Runs on port 8081 and provides document parsing services using Apache Tika.
 *
 * Endpoints:
 * - POST /parse   - accepts document bytes, returns extracted text + metadata as JSON
 * - GET  /health  - returns health status
 * - POST /detect  - accepts document bytes, returns detected MIME type
 */
public class TikaProcessorApp {

    private static final Logger logger = LoggerFactory.getLogger(TikaProcessorApp.class);
    private static final int PORT = 8081;

    public static void main(String[] args) {
        logger.info("========================================");
        logger.info("Starting LegacyBridge Tika Processor");
        logger.info("========================================");
        logger.info("Port: {}", PORT);

        try {
            // Initialize the document parser
            DocumentParser documentParser = new DocumentParser();
            logger.info("DocumentParser initialized successfully");

            // Create and configure HTTP server
            HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
            server.setExecutor(Executors.newFixedThreadPool(10));

            // Register request handlers
            server.createContext("/parse", new ParseHandler(documentParser));
            logger.info("Registered handler: POST /parse");

            server.createContext("/health", new HealthHandler());
            logger.info("Registered handler: GET /health");

            server.createContext("/detect", exchange -> {
                logger.debug("Received request: {} /detect", exchange.getRequestMethod());
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    String errorJson = "{\"error\": \"Method not allowed. Use POST.\"}";
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(405, errorJson.getBytes().length);
                    exchange.getResponseBody().write(errorJson.getBytes());
                    exchange.getResponseBody().close();
                    return;
                }

                try {
                    byte[] requestBody = exchange.getRequestBody().readAllBytes();
                    logger.debug("Detect request body size: {} bytes", requestBody.length);

                    java.io.ByteArrayInputStream inputStream = new java.io.ByteArrayInputStream(requestBody);
                    String mimeType = documentParser.detectType(inputStream);

                    String responseJson = String.format("{\"contentType\": \"%s\"}", mimeType);
                    byte[] responseBytes = responseJson.getBytes("UTF-8");

                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, responseBytes.length);
                    exchange.getResponseBody().write(responseBytes);
                    exchange.getResponseBody().close();

                    logger.info("Detected MIME type: {}", mimeType);
                } catch (Exception e) {
                    logger.error("Error detecting document type: {}", e.getMessage(), e);
                    String errorJson = String.format("{\"error\": \"%s\"}", e.getMessage());
                    byte[] errorBytes = errorJson.getBytes("UTF-8");
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(500, errorBytes.length);
                    exchange.getResponseBody().write(errorBytes);
                    exchange.getResponseBody().close();
                }
            });
            logger.info("Registered handler: POST /detect");

            // Add shutdown hook for graceful shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutdown signal received. Stopping Tika Processor...");
                server.stop(5);
                logger.info("Tika Processor stopped gracefully.");
            }));

            // Start the server
            server.start();
            logger.info("========================================");
            logger.info("Tika Processor started on port {}", PORT);
            logger.info("Endpoints:");
            logger.info("  POST http://localhost:{}/parse", PORT);
            logger.info("  POST http://localhost:{}/detect", PORT);
            logger.info("  GET  http://localhost:{}/health", PORT);
            logger.info("========================================");

        } catch (IOException e) {
            logger.error("Failed to start Tika Processor on port {}: {}", PORT, e.getMessage(), e);
            System.exit(1);
        }
    }
}
