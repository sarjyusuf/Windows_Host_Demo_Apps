package com.dragonracing.media.servlet;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class HealthServlet extends HttpServlet {

    private final String mediaDir;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HealthServlet(String mediaDir) {
        this.mediaDir = mediaDir;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        File dir = new File(mediaDir);
        String[] files = dir.list();
        int fileCount = files != null ? files.length : 0;

        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("service", "media-service");
        health.put("mediaDir", mediaDir);
        health.put("storedFiles", fileCount);
        health.put("timestamp", System.currentTimeMillis());

        resp.setContentType("application/json");
        resp.setStatus(HttpServletResponse.SC_OK);
        objectMapper.writeValue(resp.getOutputStream(), health);
    }
}
