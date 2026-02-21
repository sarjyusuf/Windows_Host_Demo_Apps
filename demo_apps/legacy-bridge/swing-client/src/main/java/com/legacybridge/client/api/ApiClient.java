package com.legacybridge.client.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * HTTP client that communicates with the LegacyBridge REST API.
 * Uses Apache HttpClient for HTTP operations and Jackson for JSON deserialization.
 */
public class ApiClient {

    private static final Logger logger = LoggerFactory.getLogger(ApiClient.class);

    private static final String DEFAULT_BASE_URL = "http://localhost:8080/api";
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int SOCKET_TIMEOUT_MS = 30000;

    private final String baseUrl;
    private final ObjectMapper objectMapper;
    private final CloseableHttpClient httpClient;

    /**
     * Creates an ApiClient with the default base URL.
     */
    public ApiClient() {
        this(DEFAULT_BASE_URL);
    }

    /**
     * Creates an ApiClient with a custom base URL.
     *
     * @param baseUrl the base URL of the REST API
     */
    public ApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.objectMapper = new ObjectMapper();

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(CONNECT_TIMEOUT_MS)
                .setSocketTimeout(SOCKET_TIMEOUT_MS)
                .setConnectionRequestTimeout(CONNECT_TIMEOUT_MS)
                .build();

        this.httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build();

        logger.info("ApiClient initialized with base URL: {}", baseUrl);
    }

    /**
     * Retrieves all documents from the REST API.
     *
     * @return list of documents, or empty list if an error occurs
     */
    public List<Document> getAllDocuments() {
        String url = baseUrl + "/documents";
        logger.debug("GET {}", url);

        HttpGet request = new HttpGet(url);
        request.setHeader("Accept", "application/json");

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

            if (statusCode == 200) {
                List<Document> documents = objectMapper.readValue(body, new TypeReference<List<Document>>() {});
                logger.debug("Retrieved {} documents", documents.size());
                return documents;
            } else {
                logger.warn("GET /documents returned status {}: {}", statusCode, body);
                return Collections.emptyList();
            }
        } catch (IOException e) {
            logger.error("Failed to retrieve documents", e);
            return Collections.emptyList();
        }
    }

    /**
     * Retrieves a single document by ID.
     *
     * @param id the document ID
     * @return the document, or null if not found or an error occurs
     */
    public Document getDocument(String id) {
        String url = baseUrl + "/documents/" + id;
        logger.debug("GET {}", url);

        HttpGet request = new HttpGet(url);
        request.setHeader("Accept", "application/json");

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

            if (statusCode == 200) {
                Document document = objectMapper.readValue(body, Document.class);
                logger.debug("Retrieved document: {}", document.getName());
                return document;
            } else {
                logger.warn("GET /documents/{} returned status {}: {}", id, statusCode, body);
                return null;
            }
        } catch (IOException e) {
            logger.error("Failed to retrieve document {}", id, e);
            return null;
        }
    }

    /**
     * Uploads a document file to the REST API.
     *
     * @param file the file to upload
     * @return the created document, or null if an error occurs
     */
    public Document uploadDocument(File file) {
        String url = baseUrl + "/documents";
        logger.debug("POST {} (file: {})", url, file.getName());

        HttpPost request = new HttpPost(url);

        HttpEntity entity = MultipartEntityBuilder.create()
                .addPart("file", new FileBody(file))
                .build();
        request.setEntity(entity);

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

            if (statusCode == 200 || statusCode == 201) {
                Document document = objectMapper.readValue(body, Document.class);
                logger.info("Uploaded document: {}", document.getName());
                return document;
            } else {
                logger.warn("POST /documents returned status {}: {}", statusCode, body);
                return null;
            }
        } catch (IOException e) {
            logger.error("Failed to upload document {}", file.getName(), e);
            return null;
        }
    }

    /**
     * Deletes a document by ID.
     *
     * @param id the document ID
     * @throws IOException if the request fails or returns a non-success status
     */
    public void deleteDocument(String id) throws IOException {
        String url = baseUrl + "/documents/" + id;
        logger.debug("DELETE {}", url);

        HttpDelete request = new HttpDelete(url);

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode == 200 || statusCode == 204) {
                logger.info("Deleted document: {}", id);
            } else {
                String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                logger.warn("DELETE /documents/{} returned status {}: {}", id, statusCode, body);
                throw new IOException("Delete failed with status " + statusCode + ": " + body);
            }
        }
    }

    /**
     * Searches for documents matching the given query.
     *
     * @param query the search query string
     * @return list of search results, or empty list if an error occurs
     */
    public List<SearchResult> searchDocuments(String query) {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = baseUrl + "/search?q=" + encodedQuery;
        logger.debug("GET {}", url);

        HttpGet request = new HttpGet(url);
        request.setHeader("Accept", "application/json");

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

            if (statusCode == 200) {
                List<SearchResult> results = objectMapper.readValue(body, new TypeReference<List<SearchResult>>() {});
                logger.debug("Search returned {} results", results.size());
                return results;
            } else {
                logger.warn("GET /search returned status {}: {}", statusCode, body);
                return Collections.emptyList();
            }
        } catch (IOException e) {
            logger.error("Failed to search documents", e);
            return Collections.emptyList();
        }
    }

    /**
     * Checks the health of the REST API.
     *
     * @return true if the API is reachable and healthy, false otherwise
     */
    public boolean checkHealth() {
        String url = baseUrl + "/health";

        HttpGet request = new HttpGet(url);
        request.setHeader("Accept", "application/json");

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            boolean healthy = statusCode == 200;
            logger.debug("Health check: {}", healthy ? "OK" : "FAIL (status " + statusCode + ")");
            return healthy;
        } catch (IOException e) {
            logger.debug("Health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Closes the underlying HTTP client and releases resources.
     */
    public void close() {
        try {
            httpClient.close();
            logger.info("ApiClient closed");
        } catch (IOException e) {
            logger.warn("Error closing HTTP client", e);
        }
    }
}
