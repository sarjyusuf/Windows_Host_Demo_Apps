package com.legacybridge.batch.jobs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

/**
 * Quartz Job that triggers a full reindex of all documents.
 * Calls the REST API to get all documents, then for each document:
 * 1. Calls the Lucene search service /index endpoint to reindex it
 * 2. Sends a JMS message to "document.reindex" queue to notify other services
 *
 * Runs every 10 minutes.
 */
public class ReindexJob implements Job {

    private static final Logger logger = LoggerFactory.getLogger(ReindexJob.class);
    private static final String REST_API_URL = "http://localhost:8080/api/documents";
    private static final String LUCENE_INDEX_URL = "http://localhost:8082/index";
    private static final String ACTIVEMQ_BROKER_URL = "tcp://localhost:61616";
    private static final String REINDEX_QUEUE = "document.reindex";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        logger.info("=== ReindexJob started ===");
        long startTime = System.currentTimeMillis();
        int totalDocuments = 0;
        int successCount = 0;
        int failCount = 0;

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            // Step 1: Fetch all documents from REST API
            logger.info("Fetching all documents from REST API: {}", REST_API_URL);

            HttpGet request = new HttpGet(REST_API_URL);
            request.setHeader("Accept", "application/json");

            String responseBody;
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                int statusCode = response.getStatusLine().getStatusCode();
                logger.debug("REST API response status: {}", statusCode);

                if (statusCode != 200) {
                    logger.warn("REST API returned non-200 status: {}. Skipping reindex.", statusCode);
                    return;
                }

                responseBody = EntityUtils.toString(response.getEntity());
            }

            JsonNode documents = objectMapper.readTree(responseBody);

            if (!documents.isArray()) {
                logger.warn("Expected JSON array from REST API, got: {}. Skipping reindex.",
                        documents.getNodeType());
                return;
            }

            totalDocuments = documents.size();
            logger.info("Found {} documents to reindex", totalDocuments);

            // Step 2: Reindex each document in Lucene
            for (JsonNode doc : documents) {
                String docId = doc.has("id") ? doc.get("id").asText() : null;
                String docName = doc.has("name") ? doc.get("name").asText() : "unknown";
                String docText = doc.has("text") ? doc.get("text").asText() : "";

                if (docId == null) {
                    logger.warn("Skipping document with no ID: {}", doc);
                    failCount++;
                    continue;
                }

                try {
                    // Call Lucene /index endpoint
                    ObjectNode indexRequest = objectMapper.createObjectNode();
                    indexRequest.put("id", docId);
                    indexRequest.put("name", docName);
                    indexRequest.put("text", docText);

                    HttpPost indexPost = new HttpPost(LUCENE_INDEX_URL);
                    indexPost.setHeader("Content-Type", "application/json");
                    indexPost.setEntity(new StringEntity(objectMapper.writeValueAsString(indexRequest)));

                    try (CloseableHttpResponse indexResponse = httpClient.execute(indexPost)) {
                        int indexStatus = indexResponse.getStatusLine().getStatusCode();
                        if (indexStatus == 200) {
                            successCount++;
                            logger.debug("Reindexed document ID: {}, Name: {}", docId, docName);
                        } else {
                            failCount++;
                            logger.warn("Failed to reindex document ID: {}, HTTP status: {}",
                                    docId, indexStatus);
                        }
                    }
                } catch (Exception e) {
                    failCount++;
                    logger.error("Error reindexing document ID: {}: {}", docId, e.getMessage());
                }
            }

            // Step 3: Send JMS notification
            sendReindexNotification(totalDocuments, successCount, failCount);

        } catch (Exception e) {
            logger.error("Error during reindex job: {}", e.getMessage(), e);
        }

        long elapsed = System.currentTimeMillis() - startTime;
        logger.info("=== ReindexJob completed in {} ms. Total: {}, Success: {}, Failed: {} ===",
                elapsed, totalDocuments, successCount, failCount);
    }

    /**
     * Sends a JMS message to the "document.reindex" queue to notify other services
     * that a reindex has been completed.
     */
    private void sendReindexNotification(int total, int success, int failed) {
        logger.info("Sending reindex notification to JMS queue: {}", REINDEX_QUEUE);

        Connection connection = null;
        Session session = null;

        try {
            ConnectionFactory connectionFactory = new ActiveMQConnectionFactory(ACTIVEMQ_BROKER_URL);
            connection = connectionFactory.createConnection();
            connection.start();

            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue(REINDEX_QUEUE);
            MessageProducer producer = session.createProducer(queue);

            ObjectNode notification = objectMapper.createObjectNode();
            notification.put("event", "reindex_complete");
            notification.put("totalDocuments", total);
            notification.put("successCount", success);
            notification.put("failCount", failed);
            notification.put("timestamp", System.currentTimeMillis());

            TextMessage message = session.createTextMessage(objectMapper.writeValueAsString(notification));
            producer.send(message);

            logger.info("Reindex notification sent successfully to queue: {}", REINDEX_QUEUE);

            producer.close();
        } catch (Exception e) {
            logger.error("Failed to send reindex notification to JMS: {}", e.getMessage(), e);
            // Don't fail the job just because JMS notification failed
        } finally {
            try {
                if (session != null) session.close();
                if (connection != null) connection.close();
            } catch (Exception e) {
                logger.warn("Error closing JMS resources: {}", e.getMessage());
            }
        }
    }
}
