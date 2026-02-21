package com.legacybridge.search.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legacybridge.search.index.IndexManager;
import com.legacybridge.search.index.SearchResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP handler for the /search endpoint.
 * Accepts GET requests with a ?q=query parameter.
 * Calls IndexManager.search() and returns a JSON array of SearchResult objects.
 */
public class SearchHandler implements HttpHandler {

    private static final Logger logger = LoggerFactory.getLogger(SearchHandler.class);
    private static final int DEFAULT_MAX_RESULTS = 20;

    private final IndexManager indexManager;
    private final ObjectMapper objectMapper;

    public SearchHandler(IndexManager indexManager) {
        this.indexManager = indexManager;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        logger.info("Received {} request to /search from {}",
                exchange.getRequestMethod(), exchange.getRemoteAddress());

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            logger.warn("Method not allowed: {}", exchange.getRequestMethod());
            sendJsonResponse(exchange, 405, "{\"error\": \"Method not allowed. Use GET.\"}");
            return;
        }

        long startTime = System.currentTimeMillis();

        try {
            // Parse query parameters
            Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI());
            String query = queryParams.get("q");
            int maxResults = DEFAULT_MAX_RESULTS;

            if (queryParams.containsKey("max")) {
                try {
                    maxResults = Integer.parseInt(queryParams.get("max"));
                } catch (NumberFormatException e) {
                    logger.warn("Invalid max parameter: {}", queryParams.get("max"));
                }
            }

            if (query == null || query.trim().isEmpty()) {
                logger.warn("Missing or empty query parameter 'q'");
                sendJsonResponse(exchange, 400,
                        "{\"error\": \"Missing required query parameter 'q'. Usage: /search?q=your+query\"}");
                return;
            }

            logger.info("Searching for: '{}' (max: {})", query, maxResults);

            // Perform the search
            List<SearchResult> results = indexManager.search(query, maxResults);

            // Serialize results to JSON
            String responseJson = objectMapper.writeValueAsString(results);

            long elapsed = System.currentTimeMillis() - startTime;
            logger.info("Search complete in {} ms. Query: '{}', Results: {}", elapsed, query, results.size());

            sendJsonResponse(exchange, 200, responseJson);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            logger.error("Error searching after {} ms: {}", elapsed, e.getMessage(), e);

            String errorJson = String.format("{\"error\": \"Search failed: %s\"}",
                    e.getMessage().replace("\"", "\\\""));
            sendJsonResponse(exchange, 500, errorJson);
        }
    }

    /**
     * Parses query parameters from a URI.
     *
     * @param uri the request URI
     * @return a map of query parameter names to values
     */
    private Map<String, String> parseQueryParams(URI uri) {
        Map<String, String> params = new HashMap<>();
        String queryString = uri.getQuery();

        if (queryString != null && !queryString.isEmpty()) {
            for (String param : queryString.split("&")) {
                String[] keyValue = param.split("=", 2);
                if (keyValue.length == 2) {
                    try {
                        String key = java.net.URLDecoder.decode(keyValue[0], "UTF-8");
                        String value = java.net.URLDecoder.decode(keyValue[1], "UTF-8");
                        params.put(key, value);
                    } catch (java.io.UnsupportedEncodingException e) {
                        params.put(keyValue[0], keyValue[1]);
                    }
                } else if (keyValue.length == 1) {
                    params.put(keyValue[0], "");
                }
            }
        }

        logger.debug("Parsed query params: {}", params);
        return params;
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
