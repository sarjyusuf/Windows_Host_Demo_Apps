package com.dragonracing.media.servlet;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class UploadServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(UploadServlet.class);
    private final String mediaDir;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public UploadServlet(String mediaDir) {
        this.mediaDir = mediaDir;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json");

        String contentType = req.getContentType();
        if (contentType == null || !contentType.toLowerCase().contains("multipart")) {
            // Fallback: accept raw binary upload with filename header
            String filename = req.getHeader("X-Filename");
            if (filename == null || filename.isBlank()) {
                filename = UUID.randomUUID().toString() + ".bin";
            }

            // Sanitize filename
            filename = sanitizeFilename(filename);

            Path filePath = Paths.get(mediaDir, filename);
            try (InputStream is = req.getInputStream();
                 OutputStream os = Files.newOutputStream(filePath)) {
                is.transferTo(os);
            }

            log.info("File uploaded (raw): {}", filename);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "uploaded");
            result.put("filename", filename);
            result.put("path", filePath.toString());
            result.put("url", "/images/" + filename);
            result.put("message", "Dragon media uploaded successfully!");

            resp.setStatus(HttpServletResponse.SC_CREATED);
            objectMapper.writeValue(resp.getOutputStream(), result);
            return;
        }

        // Simple multipart handling
        try {
            String boundary = extractBoundary(contentType);
            if (boundary == null) {
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid multipart boundary");
                return;
            }

            byte[] body = req.getInputStream().readAllBytes();
            String bodyStr = new String(body);

            // Find filename from Content-Disposition
            String filename = null;
            int filenameIdx = bodyStr.indexOf("filename=\"");
            if (filenameIdx >= 0) {
                int start = filenameIdx + 10;
                int end = bodyStr.indexOf("\"", start);
                if (end > start) {
                    filename = bodyStr.substring(start, end);
                }
            }

            if (filename == null || filename.isBlank()) {
                filename = UUID.randomUUID().toString() + ".dat";
            }
            filename = sanitizeFilename(filename);

            // Find the file content (after double CRLF following headers)
            String separator = "--" + boundary;
            int contentStart = bodyStr.indexOf("\r\n\r\n");
            int contentEnd = bodyStr.lastIndexOf(separator);

            if (contentStart < 0 || contentEnd < 0) {
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Malformed multipart data");
                return;
            }

            contentStart += 4; // skip \r\n\r\n
            byte[] fileContent = new byte[contentEnd - contentStart - 2]; // -2 for trailing \r\n
            System.arraycopy(body, contentStart, fileContent, 0, fileContent.length);

            Path filePath = Paths.get(mediaDir, filename);
            Files.write(filePath, fileContent);

            log.info("File uploaded (multipart): {} ({} bytes)", filename, fileContent.length);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "uploaded");
            result.put("filename", filename);
            result.put("size", fileContent.length);
            result.put("path", filePath.toString());
            result.put("url", "/images/" + filename);
            result.put("message", "Dragon media uploaded successfully!");

            resp.setStatus(HttpServletResponse.SC_CREATED);
            objectMapper.writeValue(resp.getOutputStream(), result);

        } catch (Exception e) {
            log.error("Upload failed", e);
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Upload failed: " + e.getMessage());
        }
    }

    private String extractBoundary(String contentType) {
        for (String part : contentType.split(";")) {
            part = part.trim();
            if (part.startsWith("boundary=")) {
                return part.substring(9).replace("\"", "");
            }
        }
        return null;
    }

    private String sanitizeFilename(String filename) {
        // Remove path separators and dangerous characters
        return filename.replaceAll("[/\\\\:*?\"<>|]", "_");
    }

    private void sendError(HttpServletResponse resp, int status, String message) throws IOException {
        resp.setStatus(status);
        objectMapper.writeValue(resp.getOutputStream(), Map.of("error", message));
    }
}
