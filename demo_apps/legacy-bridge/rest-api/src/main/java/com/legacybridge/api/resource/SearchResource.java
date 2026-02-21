package com.legacybridge.api.resource;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * JAX-RS resource that proxies search queries to the Lucene search service.
 * The Lucene search service runs as a separate JVM process on port 8082.
 */
@Path("/search")
@Produces(MediaType.APPLICATION_JSON)
public class SearchResource {

    private static final Logger logger = LoggerFactory.getLogger(SearchResource.class);
    private static final String LUCENE_SEARCH_URL = "http://localhost:8082/search";

    /**
     * GET /search?q={query} - Proxies a search query to the Lucene search service.
     * Returns the search results as JSON directly from the search service.
     */
    @GET
    public Response searchDocuments(@QueryParam("q") String query) {
        if (query == null || query.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"Query parameter 'q' is required\"}")
                    .build();
        }

        logger.info("GET /search?q={} - proxying to Lucene search service", query);

        try {
            String encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name());
            String searchUrl = LUCENE_SEARCH_URL + "?q=" + encodedQuery;

            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                HttpGet request = new HttpGet(searchUrl);
                request.setHeader("Accept", "application/json");

                try (CloseableHttpResponse searchResponse = httpClient.execute(request)) {
                    int statusCode = searchResponse.getStatusLine().getStatusCode();
                    String responseBody = EntityUtils.toString(searchResponse.getEntity(), StandardCharsets.UTF_8);

                    if (statusCode == 200) {
                        logger.info("Search proxy returned results for query: {}", query);
                        return Response.ok(responseBody, MediaType.APPLICATION_JSON).build();
                    } else {
                        logger.warn("Lucene search service returned status {} for query: {}", statusCode, query);
                        return Response.status(statusCode)
                                .entity("{\"error\": \"Search service returned status " + statusCode + "\"}")
                                .build();
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error proxying search request for query: {}", query, e);
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity("{\"error\": \"Search service is unavailable: " + e.getMessage() + "\"}")
                    .build();
        }
    }
}
