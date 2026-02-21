package com.legacybridge.batch.jobs;

import org.apache.http.client.config.RequestConfig;
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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Quartz Job that pings the health endpoints of all LegacyBridge services
 * and logs their status. Acts as a simple monitoring system for the multi-JVM
 * application.
 *
 * Services monitored:
 * - REST API:            http://localhost:8080/api/health
 * - Tika Processor:      http://localhost:8081/health
 * - Lucene Search:       http://localhost:8082/health
 * - Document Processor:  http://localhost:8083/health
 *
 * Runs every 1 minute.
 */
public class HealthCheckJob implements Job {

    private static final Logger logger = LoggerFactory.getLogger(HealthCheckJob.class);

    // Timeout for each health check request (5 seconds)
    private static final int TIMEOUT_MS = 5000;

    // Service health endpoints
    private static final Map<String, String> SERVICE_ENDPOINTS = new LinkedHashMap<>();

    static {
        SERVICE_ENDPOINTS.put("REST API", "http://localhost:8080/api/health");
        SERVICE_ENDPOINTS.put("Tika Processor", "http://localhost:8081/health");
        SERVICE_ENDPOINTS.put("Lucene Search", "http://localhost:8082/health");
        SERVICE_ENDPOINTS.put("Document Processor", "http://localhost:8083/health");
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        logger.info("=== HealthCheckJob started ===");
        long startTime = System.currentTimeMillis();

        int totalServices = SERVICE_ENDPOINTS.size();
        int upCount = 0;
        int downCount = 0;

        // Configure HTTP client with timeout
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(TIMEOUT_MS)
                .setSocketTimeout(TIMEOUT_MS)
                .setConnectionRequestTimeout(TIMEOUT_MS)
                .build();

        try (CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build()) {

            for (Map.Entry<String, String> entry : SERVICE_ENDPOINTS.entrySet()) {
                String serviceName = entry.getKey();
                String healthUrl = entry.getValue();

                boolean isUp = checkServiceHealth(httpClient, serviceName, healthUrl);
                if (isUp) {
                    upCount++;
                } else {
                    downCount++;
                }
            }

        } catch (Exception e) {
            logger.error("Error during health check: {}", e.getMessage(), e);
        }

        long elapsed = System.currentTimeMillis() - startTime;

        // Log summary
        if (downCount == 0) {
            logger.info("=== HealthCheckJob completed in {} ms. All {} services UP ===",
                    elapsed, totalServices);
        } else {
            logger.warn("=== HealthCheckJob completed in {} ms. UP: {}, DOWN: {} (of {}) ===",
                    elapsed, upCount, downCount, totalServices);
        }
    }

    /**
     * Checks the health of a single service by calling its health endpoint.
     *
     * @param httpClient  the HTTP client to use
     * @param serviceName the human-readable service name
     * @param healthUrl   the health endpoint URL
     * @return true if the service is UP, false otherwise
     */
    private boolean checkServiceHealth(CloseableHttpClient httpClient, String serviceName, String healthUrl) {
        long checkStart = System.currentTimeMillis();

        try {
            HttpGet request = new HttpGet(healthUrl);
            request.setHeader("Accept", "application/json");

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity());
                long checkElapsed = System.currentTimeMillis() - checkStart;

                if (statusCode == 200) {
                    logger.info("[UP]   {} - {} ({}ms) - Response: {}",
                            serviceName, healthUrl, checkElapsed, responseBody);
                    return true;
                } else {
                    logger.warn("[DOWN] {} - {} ({}ms) - HTTP {}: {}",
                            serviceName, healthUrl, checkElapsed, statusCode, responseBody);
                    return false;
                }
            }

        } catch (Exception e) {
            long checkElapsed = System.currentTimeMillis() - checkStart;
            logger.warn("[DOWN] {} - {} ({}ms) - Error: {}",
                    serviceName, healthUrl, checkElapsed, e.getMessage());
            return false;
        }
    }
}
