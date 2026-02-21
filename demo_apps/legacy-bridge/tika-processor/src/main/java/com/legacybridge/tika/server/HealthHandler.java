package com.legacybridge.tika.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;

/**
 * HTTP handler for the /health endpoint.
 * Returns health status information for the Tika Processor service,
 * including the Tika version being used.
 */
public class HealthHandler implements HttpHandler {

    private static final Logger logger = LoggerFactory.getLogger(HealthHandler.class);

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        logger.debug("Health check requested from {}", exchange.getRemoteAddress());

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            String errorJson = "{\"error\": \"Method not allowed. Use GET.\"}";
            byte[] errorBytes = errorJson.getBytes("UTF-8");
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(405, errorBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(errorBytes);
            }
            return;
        }

        // Get Tika version from the package info
        String tikaVersion = Tika.class.getPackage().getImplementationVersion();
        if (tikaVersion == null) {
            tikaVersion = "unknown";
        }

        String responseJson = String.format(
                "{\"status\": \"UP\", \"service\": \"tika-processor\", \"tikaVersion\": \"%s\", \"timestamp\": %d, \"jvmFreeMemory\": %d, \"jvmTotalMemory\": %d}",
                tikaVersion,
                System.currentTimeMillis(),
                Runtime.getRuntime().freeMemory(),
                Runtime.getRuntime().totalMemory()
        );

        byte[] responseBytes = responseJson.getBytes("UTF-8");
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(200, responseBytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }

        logger.debug("Health check response sent: status=UP, tikaVersion={}", tikaVersion);
    }
}
