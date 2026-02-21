package com.legacybridge.processor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;

/**
 * Service that orchestrates the full document processing pipeline:
 * 1. Calls Tika at http://localhost:8081/parse to extract text from the document
 * 2. Calls Lucene at http://localhost:8082/index to index the document for search
 * 3. Calls the REST API at http://localhost:8080/api/documents/{id}/status to update status to "PROCESSED"
 *
 * Uses RestTemplate for all HTTP calls and logs each step extensively for traceability.
 */
@Service
public class ProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(ProcessingService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${tika.url:http://localhost:8081}")
    private String tikaBaseUrl;

    @Value("${lucene.url:http://localhost:8082}")
    private String luceneBaseUrl;

    @Value("${restapi.url:http://localhost:8080}")
    private String restApiBaseUrl;

    public ProcessingService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Processes a document through the full pipeline: text extraction, indexing, and status update.
     *
     * @param documentId   the unique document identifier
     * @param documentName the document file name
     * @param contentBase64 the document content encoded as base64
     */
    public void processDocument(String documentId, String documentName, String contentBase64) {
        logger.info("=== Starting document processing pipeline for document ID: {} ===", documentId);
        long startTime = System.currentTimeMillis();

        try {
            // Step 1: Extract text using Tika
            logger.info("Step 1/3: Extracting text via Tika for document: {}", documentName);
            String extractedText = callTikaForTextExtraction(contentBase64);
            logger.info("Step 1/3 complete: Extracted {} characters of text from document: {}",
                    extractedText.length(), documentName);

            // Step 2: Index document using Lucene
            logger.info("Step 2/3: Indexing document in Lucene - ID: {}, Name: {}", documentId, documentName);
            indexDocumentInLucene(documentId, documentName, extractedText);
            logger.info("Step 2/3 complete: Document indexed successfully - ID: {}", documentId);

            // Step 3: Update document status via REST API
            logger.info("Step 3/3: Updating document status to PROCESSED - ID: {}", documentId);
            updateDocumentStatus(documentId, "PROCESSED");
            logger.info("Step 3/3 complete: Document status updated to PROCESSED - ID: {}", documentId);

            long elapsed = System.currentTimeMillis() - startTime;
            logger.info("=== Document processing pipeline complete for ID: {} in {} ms ===", documentId, elapsed);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            logger.error("=== Document processing pipeline FAILED for ID: {} after {} ms ===",
                    documentId, elapsed, e);

            // Attempt to update status to FAILED
            try {
                updateDocumentStatus(documentId, "FAILED");
                logger.info("Document status updated to FAILED for ID: {}", documentId);
            } catch (Exception statusEx) {
                logger.error("Could not update status to FAILED for document ID: {}", documentId, statusEx);
            }

            throw new RuntimeException("Document processing failed for ID: " + documentId, e);
        }
    }

    /**
     * Calls the Tika processor to extract text from the document content.
     *
     * @param contentBase64 the document content as base64
     * @return the extracted text
     */
    private String callTikaForTextExtraction(String contentBase64) {
        String tikaUrl = tikaBaseUrl + "/parse";
        logger.debug("Calling Tika at: {}", tikaUrl);

        try {
            byte[] documentBytes = Base64.getDecoder().decode(contentBase64);
            logger.debug("Decoded document content: {} bytes", documentBytes.length);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

            HttpEntity<byte[]> request = new HttpEntity<>(documentBytes, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    tikaUrl, HttpMethod.POST, request, String.class);

            logger.debug("Tika response status: {}", response.getStatusCode());

            if (response.getBody() == null) {
                throw new RuntimeException("Tika returned empty response");
            }

            JsonNode responseJson = objectMapper.readTree(response.getBody());
            String extractedText = responseJson.get("text").asText();

            logger.debug("Tika extracted text preview: {}...",
                    extractedText.substring(0, Math.min(200, extractedText.length())));

            return extractedText;

        } catch (RestClientException e) {
            logger.error("HTTP error calling Tika at {}: {}", tikaUrl, e.getMessage());
            throw new RuntimeException("Failed to call Tika for text extraction", e);
        } catch (Exception e) {
            logger.error("Error processing Tika response: {}", e.getMessage());
            throw new RuntimeException("Failed to process Tika response", e);
        }
    }

    /**
     * Calls the Lucene search service to index the document.
     *
     * @param documentId   the document ID
     * @param documentName the document name
     * @param text         the extracted text to index
     */
    private void indexDocumentInLucene(String documentId, String documentName, String text) {
        String luceneUrl = luceneBaseUrl + "/index";
        logger.debug("Calling Lucene index at: {}", luceneUrl);

        try {
            ObjectNode indexRequest = objectMapper.createObjectNode();
            indexRequest.put("id", documentId);
            indexRequest.put("name", documentName);
            indexRequest.put("text", text);

            String jsonBody = objectMapper.writeValueAsString(indexRequest);
            logger.debug("Lucene index request body size: {} bytes", jsonBody.length());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    luceneUrl, HttpMethod.POST, request, String.class);

            logger.debug("Lucene index response status: {}", response.getStatusCode());
            logger.debug("Lucene index response body: {}", response.getBody());

        } catch (RestClientException e) {
            logger.error("HTTP error calling Lucene at {}: {}", luceneUrl, e.getMessage());
            throw new RuntimeException("Failed to index document in Lucene", e);
        } catch (Exception e) {
            logger.error("Error during Lucene indexing: {}", e.getMessage());
            throw new RuntimeException("Failed to index document", e);
        }
    }

    /**
     * Calls the REST API to update the document status.
     *
     * @param documentId the document ID
     * @param status     the new status (e.g., "PROCESSED", "FAILED")
     */
    private void updateDocumentStatus(String documentId, String status) {
        String statusUrl = restApiBaseUrl + "/api/documents/" + documentId + "/status";
        logger.debug("Updating document status at: {} to {}", statusUrl, status);

        try {
            ObjectNode statusRequest = objectMapper.createObjectNode();
            statusRequest.put("status", status);

            String jsonBody = objectMapper.writeValueAsString(statusRequest);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    statusUrl, HttpMethod.PUT, request, String.class);

            logger.debug("Status update response: {} - {}", response.getStatusCode(), response.getBody());

        } catch (RestClientException e) {
            logger.error("HTTP error updating status at {}: {}", statusUrl, e.getMessage());
            throw new RuntimeException("Failed to update document status", e);
        } catch (Exception e) {
            logger.error("Error updating document status: {}", e.getMessage());
            throw new RuntimeException("Failed to update document status", e);
        }
    }
}
