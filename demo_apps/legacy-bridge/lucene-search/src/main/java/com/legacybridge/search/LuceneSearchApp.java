package com.legacybridge.search;

import com.legacybridge.search.index.IndexManager;
import com.legacybridge.search.server.HealthHandler;
import com.legacybridge.search.server.IndexHandler;
import com.legacybridge.search.server.SearchHandler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;

/**
 * Lucene Search Application - Standalone JAR with embedded HTTP server.
 * Runs on port 8082. Maintains a Lucene index for full-text document search.
 *
 * Endpoints:
 * - POST /index  - index a document (accepts JSON: id, name, text)
 * - GET  /search - search with ?q=query parameter, returns JSON array of hits
 * - GET  /health - health status
 * - GET  /stats  - index statistics (document count, etc.)
 *
 * Index directory: ./data/lucene-index
 */
public class LuceneSearchApp {

    private static final Logger logger = LoggerFactory.getLogger(LuceneSearchApp.class);
    private static final int PORT = 8082;
    private static final String INDEX_DIR = "./data/lucene-index";

    public static void main(String[] args) {
        logger.info("========================================");
        logger.info("Starting LegacyBridge Lucene Search Service");
        logger.info("========================================");
        logger.info("Port: {}", PORT);
        logger.info("Index directory: {}", INDEX_DIR);

        try {
            // Initialize the index manager
            Path indexPath = Paths.get(INDEX_DIR);
            IndexManager indexManager = new IndexManager(indexPath);
            logger.info("IndexManager initialized. Current document count: {}", indexManager.getDocumentCount());

            // Create and configure HTTP server
            HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
            server.setExecutor(Executors.newFixedThreadPool(10));

            // Register request handlers
            server.createContext("/index", new IndexHandler(indexManager));
            logger.info("Registered handler: POST /index");

            server.createContext("/search", new SearchHandler(indexManager));
            logger.info("Registered handler: GET /search");

            server.createContext("/health", new HealthHandler(indexManager));
            logger.info("Registered handler: GET /health");

            server.createContext("/stats", exchange -> {
                logger.debug("Received {} request to /stats", exchange.getRequestMethod());
                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendJsonResponse(exchange, 405, "{\"error\": \"Method not allowed. Use GET.\"}");
                    return;
                }

                try {
                    int docCount = indexManager.getDocumentCount();
                    String responseJson = String.format(
                            "{\"documentCount\": %d, \"indexDirectory\": \"%s\", \"timestamp\": %d}",
                            docCount, INDEX_DIR.replace("\\", "\\\\"), System.currentTimeMillis());
                    sendJsonResponse(exchange, 200, responseJson);
                    logger.debug("Stats response: docCount={}", docCount);
                } catch (Exception e) {
                    logger.error("Error getting index stats: {}", e.getMessage(), e);
                    sendJsonResponse(exchange, 500,
                            String.format("{\"error\": \"%s\"}", e.getMessage()));
                }
            });
            logger.info("Registered handler: GET /stats");

            // Add shutdown hook for graceful shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutdown signal received. Stopping Lucene Search Service...");
                server.stop(5);
                try {
                    indexManager.close();
                    logger.info("IndexWriter closed successfully.");
                } catch (IOException e) {
                    logger.error("Error closing IndexWriter: {}", e.getMessage(), e);
                }
                logger.info("Lucene Search Service stopped gracefully.");
            }));

            // Start the server
            server.start();
            logger.info("========================================");
            logger.info("Lucene Search Service started on port {}", PORT);
            logger.info("Endpoints:");
            logger.info("  POST http://localhost:{}/index", PORT);
            logger.info("  GET  http://localhost:{}/search?q=<query>", PORT);
            logger.info("  GET  http://localhost:{}/health", PORT);
            logger.info("  GET  http://localhost:{}/stats", PORT);
            logger.info("========================================");

        } catch (IOException e) {
            logger.error("Failed to start Lucene Search Service on port {}: {}", PORT, e.getMessage(), e);
            System.exit(1);
        }
    }

    private static void sendJsonResponse(HttpExchange exchange, int statusCode, String jsonBody)
            throws IOException {
        byte[] responseBytes = jsonBody.getBytes("UTF-8");
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
}
