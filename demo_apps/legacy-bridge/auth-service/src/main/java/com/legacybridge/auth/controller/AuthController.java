package com.legacybridge.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Spring MVC Controller for authentication-related web pages and API endpoints.
 * Handles login/dashboard page rendering and provides JSON API endpoints
 * for auth validation and user info.
 */
@Controller
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Displays the login page.
     */
    @GetMapping("/login")
    public String loginPage() {
        logger.debug("Serving login page");
        return "login";
    }

    /**
     * Displays the authenticated dashboard.
     */
    @GetMapping("/dashboard")
    public String dashboardPage() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        logger.info("User '{}' accessed dashboard", auth != null ? auth.getName() : "anonymous");
        return "dashboard";
    }

    /**
     * API endpoint to validate the current authentication status.
     * Returns JSON: {"authenticated": true/false, "username": "...", "roles": [...]}
     */
    @GetMapping("/api/validate")
    @ResponseBody
    public void validateAuth(HttpServletResponse response) throws IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        Map<String, Object> result = new HashMap<>();

        if (auth != null && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal())) {
            result.put("authenticated", true);
            result.put("username", auth.getName());
            result.put("roles", auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList()));
            logger.debug("Auth validation: user '{}' is authenticated", auth.getName());
        } else {
            result.put("authenticated", false);
            result.put("username", null);
            result.put("roles", new String[0]);
            logger.debug("Auth validation: not authenticated");
        }

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), result);
    }

    /**
     * API endpoint to get current user information.
     * Returns JSON with user details.
     */
    @GetMapping("/api/user")
    @ResponseBody
    public void getUserInfo(HttpServletResponse response) throws IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        Map<String, Object> userInfo = new HashMap<>();

        if (auth != null && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal())) {
            userInfo.put("username", auth.getName());
            userInfo.put("roles", auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList()));
            userInfo.put("authenticated", true);
            userInfo.put("service", "auth-service");
            logger.debug("User info requested for '{}'", auth.getName());
        } else {
            userInfo.put("username", null);
            userInfo.put("authenticated", false);
            userInfo.put("service", "auth-service");
        }

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), userInfo);
    }

    /**
     * Health check endpoint for the auth service.
     */
    @GetMapping("/api/health")
    @ResponseBody
    public void healthCheck(HttpServletResponse response) throws IOException {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "auth-service");
        health.put("timestamp", System.currentTimeMillis());

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), health);
    }
}
