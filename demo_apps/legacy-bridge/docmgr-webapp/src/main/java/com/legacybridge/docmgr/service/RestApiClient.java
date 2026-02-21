package com.legacybridge.docmgr.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legacybridge.docmgr.model.Document;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * CDI ApplicationScoped bean that acts as a client to the LegacyBridge REST API.
 * Uses Apache HttpClient 4.x for HTTP communication and Jackson for JSON serialization.
 */
@ApplicationScoped
public class RestApiClient {

    private static final Logger logger = LoggerFactory.getLogger(RestApiClient.class);

    private static final String REST_API_BASE_URL = "http://localhost:8080/api";
    private static final String SEARCH_SERVICE_URL = "http://localhost:8082/search";

    private CloseableHttpClient httpClient;
    private ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        httpClient = HttpClients.createDefault();
        objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        logger.info("RestApiClient initialized. REST API URL: {}, Search URL: {}",
                REST_API_BASE_URL, SEARCH_SERVICE_URL);
    }

    @PreDestroy
    public void destroy() {
        if (httpClient != null) {
            try {
                httpClient.close();
            } catch (IOException e) {
                logger.warn("Error closing HTTP client", e);
            }
        }
    }

    /**
     * Retrieves all documents from the REST API.
     */
    public List<Document> getAllDocuments() {
        String url = REST_API_BASE_URL + "/documents";
        logger.debug("GET {}", url);

        HttpGet request = new HttpGet(url);
        request.setHeader("Accept", "application/json");

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

            if (statusCode == 200) {
                List<Document> documents = objectMapper.readValue(responseBody,
                        new TypeReference<List<Document>>() {});
                logger.info("Retrieved {} documents from REST API", documents.size());
                return documents;
            } else {
                logger.error("Failed to get documents. Status: {}, Body: {}", statusCode, responseBody);
                return Collections.emptyList();
            }
        } catch (IOException e) {
            logger.error("Error communicating with REST API at {}", url, e);
            return Collections.emptyList();
        }
    }

    /**
     * Retrieves a single document by its ID.
     */
    public Document getDocument(String id) {
        String url = REST_API_BASE_URL + "/documents/" + id;
        logger.debug("GET {}", url);

        HttpGet request = new HttpGet(url);
        request.setHeader("Accept", "application/json");

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

            if (statusCode == 200) {
                return objectMapper.readValue(responseBody, Document.class);
            } else {
                logger.error("Failed to get document {}. Status: {}", id, statusCode);
                return null;
            }
        } catch (IOException e) {
            logger.error("Error retrieving document {}", id, e);
            return null;
        }
    }

    /**
     * Uploads a document to the REST API using multipart form data.
     */
    public Document uploadDocument(String name, byte[] content, String contentType) {
        String url = REST_API_BASE_URL + "/documents";
        logger.debug("POST {} (file: {}, type: {}, size: {})", url, name, contentType, content.length);

        HttpPost request = new HttpPost(url);

        HttpEntity multipartEntity = MultipartEntityBuilder.create()
                .addBinaryBody("file", content, ContentType.create(contentType), name)
                .addTextBody("name", name, ContentType.TEXT_PLAIN)
                .build();
        request.setEntity(multipartEntity);

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

            if (statusCode == 200 || statusCode == 201) {
                // The API returns an ApiResponse wrapper; extract the data field
                Map<String, Object> apiResponse = objectMapper.readValue(responseBody,
                        new TypeReference<Map<String, Object>>() {});
                Object data = apiResponse.get("data");
                if (data != null) {
                    String dataJson = objectMapper.writeValueAsString(data);
                    Document doc = objectMapper.readValue(dataJson, Document.class);
                    logger.info("Document uploaded successfully: {}", doc.getId());
                    return doc;
                }
                // If no wrapper, try to parse directly
                return objectMapper.readValue(responseBody, Document.class);
            } else {
                logger.error("Failed to upload document. Status: {}, Body: {}", statusCode, responseBody);
                return null;
            }
        } catch (IOException e) {
            logger.error("Error uploading document '{}'", name, e);
            return null;
        }
    }

    /**
     * Searches documents using the Lucene search service (proxied through the REST API or direct).
     */
    public List<Document> searchDocuments(String query) {
        String encodedQuery;
        try {
            encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            encodedQuery = query;
        }
        String url = SEARCH_SERVICE_URL + "?q=" + encodedQuery;
        logger.debug("GET {}", url);

        HttpGet request = new HttpGet(url);
        request.setHeader("Accept", "application/json");

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

            if (statusCode == 200) {
                List<Document> results = objectMapper.readValue(responseBody,
                        new TypeReference<List<Document>>() {});
                logger.info("Search for '{}' returned {} results", query, results.size());
                return results;
            } else {
                logger.warn("Search service returned status {}. Falling back to REST API search.", statusCode);
                return searchViaRestApi(encodedQuery);
            }
        } catch (IOException e) {
            logger.warn("Search service unavailable, falling back to REST API", e);
            return searchViaRestApi(encodedQuery);
        }
    }

    /**
     * Fallback search through the REST API's search proxy endpoint.
     */
    private List<Document> searchViaRestApi(String encodedQuery) {
        String url = REST_API_BASE_URL + "/search?q=" + encodedQuery;
        logger.debug("Fallback GET {}", url);

        HttpGet request = new HttpGet(url);
        request.setHeader("Accept", "application/json");

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

            if (statusCode == 200) {
                return objectMapper.readValue(responseBody, new TypeReference<List<Document>>() {});
            } else {
                logger.error("Fallback search also failed. Status: {}", statusCode);
                return Collections.emptyList();
            }
        } catch (IOException e) {
            logger.error("Error in fallback search", e);
            return Collections.emptyList();
        }
    }

    /**
     * Deletes a document by its ID.
     */
    public void deleteDocument(String id) {
        String url = REST_API_BASE_URL + "/documents/" + id;
        logger.debug("DELETE {}", url);

        HttpDelete request = new HttpDelete(url);

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200 || statusCode == 204) {
                logger.info("Document {} deleted successfully", id);
            } else {
                logger.error("Failed to delete document {}. Status: {}", id, statusCode);
            }
        } catch (IOException e) {
            logger.error("Error deleting document {}", id, e);
        }
    }
}
