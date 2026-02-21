package com.legacybridge.tika.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.legacybridge.tika.parser.DocumentParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

/**
 * HTTP handler for the /parse endpoint.
 * Accepts POST requests with document bytes in the request body.
 * Calls DocumentParser to extract text and metadata, then returns JSON response:
 * {"text": "...", "metadata": {...}, "contentType": "..."}
 */
public class ParseHandler implements HttpHandler {

    private static final Logger logger = LoggerFactory.getLogger(ParseHandler.class);

    private final DocumentParser documentParser;
    private final ObjectMapper objectMapper;

    public ParseHandler(DocumentParser documentParser) {
        this.documentParser = documentParser;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        logger.info("Received {} request to /parse from {}",
                exchange.getRequestMethod(), exchange.getRemoteAddress());

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            logger.warn("Method not allowed: {}", exchange.getRequestMethod());
            sendJsonResponse(exchange, 405, "{\"error\": \"Method not allowed. Use POST.\"}");
            return;
        }

        long startTime = System.currentTimeMillis();

        try {
            // Read the request body (document bytes)
            byte[] requestBody = exchange.getRequestBody().readAllBytes();
            logger.info("Received document payload: {} bytes", requestBody.length);

            if (requestBody.length == 0) {
                logger.warn("Empty request body received");
                sendJsonResponse(exchange, 400, "{\"error\": \"Empty request body\"}");
                return;
            }

            // Extract text
            ByteArrayInputStream textStream = new ByteArrayInputStream(requestBody);
            String extractedText = documentParser.extractText(textStream);
            logger.debug("Extracted text length: {} characters", extractedText.length());

            // Extract metadata
            ByteArrayInputStream metadataStream = new ByteArrayInputStream(requestBody);
            Map<String, String> metadata = documentParser.extractMetadata(metadataStream);
            logger.debug("Extracted metadata entries: {}", metadata.size());

            // Detect content type
            ByteArrayInputStream typeStream = new ByteArrayInputStream(requestBody);
            String contentType = documentParser.detectType(typeStream);
            logger.debug("Detected content type: {}", contentType);

            // Build response JSON
            ObjectNode responseNode = objectMapper.createObjectNode();
            responseNode.put("text", extractedText);

            ObjectNode metadataNode = objectMapper.createObjectNode();
            for (Map.Entry<String, String> entry : metadata.entrySet()) {
                metadataNode.put(entry.getKey(), entry.getValue());
            }
            responseNode.set("metadata", metadataNode);
            responseNode.put("contentType", contentType);

            String responseJson = objectMapper.writeValueAsString(responseNode);
            long elapsed = System.currentTimeMillis() - startTime;

            logger.info("Parse complete in {} ms. Text: {} chars, Metadata: {} entries, Type: {}",
                    elapsed, extractedText.length(), metadata.size(), contentType);

            sendJsonResponse(exchange, 200, responseJson);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            logger.error("Error parsing document after {} ms: {}", elapsed, e.getMessage(), e);

            ObjectNode errorNode = objectMapper.createObjectNode();
            errorNode.put("error", "Failed to parse document: " + e.getMessage());
            String errorJson = objectMapper.writeValueAsString(errorNode);

            sendJsonResponse(exchange, 500, errorJson);
        }
    }

    /**
     * Sends a JSON response with the given status code and body.
     */
    private void sendJsonResponse(HttpExchange exchange, int statusCode, String jsonBody) throws IOException {
        byte[] responseBytes = jsonBody.getBytes("UTF-8");
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
}
