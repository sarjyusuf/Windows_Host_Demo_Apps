package com.legacybridge.search.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legacybridge.search.index.IndexManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;

/**
 * HTTP handler for the /index endpoint.
 * Accepts POST requests with JSON body containing document details (id, name, text).
 * Calls IndexManager to add or update the document in the Lucene index.
 * Returns success or failure JSON response.
 */
public class IndexHandler implements HttpHandler {

    private static final Logger logger = LoggerFactory.getLogger(IndexHandler.class);

    private final IndexManager indexManager;
    private final ObjectMapper objectMapper;

    public IndexHandler(IndexManager indexManager) {
        this.indexManager = indexManager;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        logger.info("Received {} request to /index from {}",
                exchange.getRequestMethod(), exchange.getRemoteAddress());

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            logger.warn("Method not allowed: {}", exchange.getRequestMethod());
            sendJsonResponse(exchange, 405, "{\"error\": \"Method not allowed. Use POST.\"}");
            return;
        }

        long startTime = System.currentTimeMillis();

        try {
            // Read and parse the JSON request body
            byte[] requestBody = exchange.getRequestBody().readAllBytes();
            String jsonStr = new String(requestBody, "UTF-8");
            logger.debug("Index request body: {}", jsonStr);

            JsonNode requestNode = objectMapper.readTree(jsonStr);

            // Validate required fields
            if (!requestNode.has("id") || !requestNode.has("name") || !requestNode.has("text")) {
                logger.warn("Missing required fields in index request");
                sendJsonResponse(exchange, 400,
                        "{\"error\": \"Missing required fields: id, name, text\"}");
                return;
            }

            String id = requestNode.get("id").asText();
            String name = requestNode.get("name").asText();
            String text = requestNode.get("text").asText();

            logger.info("Indexing document - ID: {}, Name: {}, Text length: {} chars",
                    id, name, text.length());

            // Index the document
            indexManager.indexDocument(id, name, text);

            long elapsed = System.currentTimeMillis() - startTime;
            int docCount = indexManager.getDocumentCount();

            String responseJson = String.format(
                    "{\"status\": \"indexed\", \"id\": \"%s\", \"name\": \"%s\", \"documentCount\": %d, \"elapsedMs\": %d}",
                    id, name.replace("\"", "\\\""), docCount, elapsed);

            logger.info("Document indexed successfully in {} ms. ID: {}, Total docs: {}",
                    elapsed, id, docCount);

            sendJsonResponse(exchange, 200, responseJson);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            logger.error("Error indexing document after {} ms: {}", elapsed, e.getMessage(), e);

            String errorJson = String.format("{\"error\": \"Failed to index document: %s\"}",
                    e.getMessage().replace("\"", "\\\""));
            sendJsonResponse(exchange, 500, errorJson);
        }
    }

    private void sendJsonResponse(HttpExchange exchange, int statusCode, String jsonBody) throws IOException {
        byte[] responseBytes = jsonBody.getBytes("UTF-8");
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
}
