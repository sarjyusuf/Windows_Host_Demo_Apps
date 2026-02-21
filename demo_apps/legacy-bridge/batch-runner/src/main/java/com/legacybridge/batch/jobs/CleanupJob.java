package com.legacybridge.batch.jobs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Quartz Job that performs cleanup of old processed documents.
 * Calls the REST API to get all documents, finds ones with status "PROCESSED"
 * older than 1 hour (for demo purposes), and logs what would be cleaned up.
 *
 * In a production system, this would actually delete or archive the old documents.
 * For this demo, it only logs the cleanup candidates.
 *
 * Runs every 5 minutes.
 */
public class CleanupJob implements Job {

    private static final Logger logger = LoggerFactory.getLogger(CleanupJob.class);
    private static final String REST_API_URL = "http://localhost:8080/api/documents";
    private static final long RETENTION_HOURS = 1; // Demo: 1 hour retention

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        logger.info("=== CleanupJob started ===");
        long startTime = System.currentTimeMillis();
        int totalDocuments = 0;
        int cleanupCandidates = 0;

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            logger.info("Fetching all documents from REST API: {}", REST_API_URL);

            HttpGet request = new HttpGet(REST_API_URL);
            request.setHeader("Accept", "application/json");

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                int statusCode = response.getStatusLine().getStatusCode();
                logger.debug("REST API response status: {}", statusCode);

                if (statusCode != 200) {
                    logger.warn("REST API returned non-200 status: {}. Skipping cleanup.", statusCode);
                    return;
                }

                String responseBody = EntityUtils.toString(response.getEntity());
                JsonNode documents = objectMapper.readTree(responseBody);

                if (!documents.isArray()) {
                    logger.warn("Expected JSON array from REST API, got: {}. Skipping cleanup.",
                            documents.getNodeType());
                    return;
                }

                Instant cutoffTime = Instant.now().minus(RETENTION_HOURS, ChronoUnit.HOURS);
                logger.info("Cleanup cutoff time: {} ({} hour(s) ago)", cutoffTime, RETENTION_HOURS);

                for (JsonNode doc : documents) {
                    totalDocuments++;
                    String docId = doc.has("id") ? doc.get("id").asText() : "unknown";
                    String docName = doc.has("name") ? doc.get("name").asText() : "unknown";
                    String status = doc.has("status") ? doc.get("status").asText() : "unknown";

                    if ("PROCESSED".equals(status)) {
                        // Check if document has a processedAt timestamp
                        if (doc.has("processedAt")) {
                            long processedAtMs = doc.get("processedAt").asLong();
                            Instant processedAt = Instant.ofEpochMilli(processedAtMs);

                            if (processedAt.isBefore(cutoffTime)) {
                                cleanupCandidates++;
                                logger.info("CLEANUP CANDIDATE: Document ID={}, Name='{}', Status={}, ProcessedAt={}",
                                        docId, docName, status, processedAt);
                                // In production, we would delete or archive here
                                // For demo, we just log
                            } else {
                                logger.debug("Document ID={} is PROCESSED but within retention period", docId);
                            }
                        } else {
                            // No timestamp, consider it a cleanup candidate
                            cleanupCandidates++;
                            logger.info("CLEANUP CANDIDATE (no timestamp): Document ID={}, Name='{}', Status={}",
                                    docId, docName, status);
                        }
                    } else {
                        logger.debug("Skipping document ID={} with status: {}", docId, status);
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Error during cleanup job: {}", e.getMessage(), e);
            // Don't throw JobExecutionException to avoid Quartz removing the job
            // Just log and let the next run try again
        }

        long elapsed = System.currentTimeMillis() - startTime;
        logger.info("=== CleanupJob completed in {} ms. Total docs: {}, Cleanup candidates: {} ===",
                elapsed, totalDocuments, cleanupCandidates);
    }
}
