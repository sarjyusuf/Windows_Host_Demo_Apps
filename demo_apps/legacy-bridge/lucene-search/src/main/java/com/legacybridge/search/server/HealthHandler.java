package com.legacybridge.search.server;

import com.legacybridge.search.index.IndexManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;

/**
 * HTTP handler for the /health endpoint.
 * Returns health status information for the Lucene Search service,
 * including the current document count in the index.
 */
public class HealthHandler implements HttpHandler {

    private static final Logger logger = LoggerFactory.getLogger(HealthHandler.class);

    private final IndexManager indexManager;

    public HealthHandler(IndexManager indexManager) {
        this.indexManager = indexManager;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        logger.debug("Health check requested from {}", exchange.getRemoteAddress());

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            String errorJson = "{\"error\": \"Method not allowed. Use GET.\"}";
            byte[] errorBytes = errorJson.getBytes("UTF-8");
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(405, errorBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(errorBytes);
            }
            return;
        }

        int docCount = indexManager.getDocumentCount();

        String responseJson = String.format(
                "{\"status\": \"UP\", \"service\": \"lucene-search\", \"documentCount\": %d, \"timestamp\": %d, \"jvmFreeMemory\": %d, \"jvmTotalMemory\": %d}",
                docCount,
                System.currentTimeMillis(),
                Runtime.getRuntime().freeMemory(),
                Runtime.getRuntime().totalMemory()
        );

        byte[] responseBytes = responseJson.getBytes("UTF-8");
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(200, responseBytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }

        logger.debug("Health check response sent: status=UP, docCount={}", docCount);
    }
}
