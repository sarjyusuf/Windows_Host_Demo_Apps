package com.dragonracing.media.servlet;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class ImageServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(ImageServlet.class);
    private final String mediaDir;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ImageServlet(String mediaDir) {
        this.mediaDir = mediaDir;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/")) {
            resp.setContentType("application/json");
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            objectMapper.writeValue(resp.getOutputStream(), Map.of("error", "Filename required: /images/{filename}"));
            return;
        }

        String filename = pathInfo.substring(1); // Remove leading /
        // Sanitize to prevent path traversal
        filename = filename.replaceAll("[/\\\\]", "");

        Path filePath = Paths.get(mediaDir, filename);
        if (!Files.exists(filePath)) {
            resp.setContentType("application/json");
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            objectMapper.writeValue(resp.getOutputStream(),
                    Map.of("error", "Image not found: " + filename));
            return;
        }

        // Determine content type
        String contentType = guessContentType(filename);
        resp.setContentType(contentType);
        resp.setStatus(HttpServletResponse.SC_OK);

        log.info("Serving image: {} ({})", filename, contentType);
        Files.copy(filePath, resp.getOutputStream());
    }

    private String guessContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".bmp")) return "image/bmp";
        return "application/octet-stream";
    }
}
