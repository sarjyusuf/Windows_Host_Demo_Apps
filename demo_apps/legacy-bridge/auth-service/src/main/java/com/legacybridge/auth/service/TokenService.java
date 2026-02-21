package com.legacybridge.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.UUID;

/**
 * Service for managing authentication tokens using H2 embedded database.
 * Tokens are UUID-based and stored with associated username and creation timestamp.
 */
@Service
public class TokenService {

    private static final Logger logger = LoggerFactory.getLogger(TokenService.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Initializes the TOKENS table in the H2 database on application startup.
     */
    @PostConstruct
    public void init() {
        logger.info("Initializing TokenService - creating TOKENS table");

        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS TOKENS (" +
                "    ID BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "    TOKEN VARCHAR(255) NOT NULL UNIQUE, " +
                "    USERNAME VARCHAR(255) NOT NULL, " +
                "    CREATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "    EXPIRES_AT TIMESTAMP, " +
                "    ACTIVE BOOLEAN DEFAULT TRUE" +
                ")"
        );

        logger.info("TOKENS table ready");
    }

    /**
     * Generates a new UUID-based token for the specified username.
     * The token is stored in the H2 database with a 24-hour expiration.
     *
     * @param username the username to associate with the token
     * @return the generated token string
     */
    public String generateToken(String username) {
        String token = UUID.randomUUID().toString();

        jdbcTemplate.update(
                "INSERT INTO TOKENS (TOKEN, USERNAME, CREATED_AT, EXPIRES_AT, ACTIVE) " +
                "VALUES (?, ?, CURRENT_TIMESTAMP, DATEADD('HOUR', 24, CURRENT_TIMESTAMP), TRUE)",
                token, username
        );

        logger.info("Generated token for user '{}': {}...", username, token.substring(0, 8));
        return token;
    }

    /**
     * Validates whether a token exists, is active, and has not expired.
     *
     * @param token the token string to validate
     * @return true if the token is valid, false otherwise
     */
    public boolean validateToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM TOKENS " +
                "WHERE TOKEN = ? AND ACTIVE = TRUE AND EXPIRES_AT > CURRENT_TIMESTAMP",
                Integer.class,
                token
        );

        boolean valid = count != null && count > 0;
        logger.debug("Token validation for '{}...': {}", token.substring(0, Math.min(8, token.length())), valid);
        return valid;
    }

    /**
     * Deactivates (revokes) a token.
     *
     * @param token the token to deactivate
     * @return true if a token was deactivated, false if token was not found
     */
    public boolean revokeToken(String token) {
        int updated = jdbcTemplate.update(
                "UPDATE TOKENS SET ACTIVE = FALSE WHERE TOKEN = ?",
                token
        );

        if (updated > 0) {
            logger.info("Token revoked: {}...", token.substring(0, Math.min(8, token.length())));
        }
        return updated > 0;
    }
}
