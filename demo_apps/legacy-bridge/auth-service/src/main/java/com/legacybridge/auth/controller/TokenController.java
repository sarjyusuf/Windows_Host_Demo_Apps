package com.legacybridge.auth.controller;

import com.legacybridge.auth.service.TokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for token management operations.
 * Provides endpoints to generate and validate authentication tokens.
 * Tokens are stored in the H2 embedded database.
 */
@RestController
@RequestMapping("/api/tokens")
public class TokenController {

    private static final Logger logger = LoggerFactory.getLogger(TokenController.class);

    @Autowired
    private TokenService tokenService;

    /**
     * Generates a new authentication token for the currently authenticated user
     * or for a specified username.
     *
     * POST /api/tokens/generate
     * Request body (optional): {"username": "someuser"}
     * Response: {"success": true, "token": "uuid-string", "username": "..."}
     */
    @PostMapping("/generate")
    public Map<String, Object> generateToken(@RequestBody(required = false) Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();

        String username;
        if (request != null && request.containsKey("username")) {
            username = request.get("username");
        } else {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            username = auth != null ? auth.getName() : "unknown";
        }

        try {
            String token = tokenService.generateToken(username);
            response.put("success", true);
            response.put("token", token);
            response.put("username", username);
            logger.info("Token generated for user '{}'", username);
        } catch (Exception e) {
            logger.error("Error generating token for user '{}'", username, e);
            response.put("success", false);
            response.put("message", "Failed to generate token: " + e.getMessage());
        }

        return response;
    }

    /**
     * Validates a given token.
     *
     * POST /api/tokens/validate
     * Request body: {"token": "uuid-string"}
     * Response: {"valid": true/false, "token": "..."}
     */
    @PostMapping("/validate")
    public Map<String, Object> validateToken(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();

        String token = request.get("token");
        if (token == null || token.trim().isEmpty()) {
            response.put("valid", false);
            response.put("message", "Token is required");
            return response;
        }

        try {
            boolean valid = tokenService.validateToken(token);
            response.put("valid", valid);
            response.put("token", token);
            logger.debug("Token validation result for '{}...': {}", token.substring(0, Math.min(8, token.length())), valid);
        } catch (Exception e) {
            logger.error("Error validating token", e);
            response.put("valid", false);
            response.put("message", "Error validating token: " + e.getMessage());
        }

        return response;
    }
}
