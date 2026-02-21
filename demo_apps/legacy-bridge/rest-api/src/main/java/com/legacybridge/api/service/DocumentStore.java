package com.legacybridge.api.service;

import com.legacybridge.api.model.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Singleton service managing document persistence using an H2 embedded database.
 * The database is file-based at ./data/legacybridge, allowing data to survive
 * application restarts.
 * Provides JDBC-based CRUD operations for documents.
 */
public class DocumentStore {

    private static final Logger logger = LoggerFactory.getLogger(DocumentStore.class);

    private static final String DB_URL = "jdbc:h2:./data/legacybridge;AUTO_SERVER=TRUE";
    private static final String DB_USER = "sa";
    private static final String DB_PASSWORD = "";

    private static DocumentStore instance;

    private DocumentStore() {
        // Load H2 driver
        try {
            Class.forName("org.h2.Driver");
            logger.info("H2 database driver loaded");
        } catch (ClassNotFoundException e) {
            logger.error("Failed to load H2 driver", e);
            throw new RuntimeException("H2 driver not found", e);
        }
    }

    /**
     * Returns the singleton instance.
     */
    public static synchronized DocumentStore getInstance() {
        if (instance == null) {
            instance = new DocumentStore();
        }
        return instance;
    }

    /**
     * Initializes the database schema. Creates the DOCUMENTS table if it does not exist.
     */
    public void initialize() {
        logger.info("Initializing DocumentStore database at {}", DB_URL);

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS DOCUMENTS (" +
                    "    ID VARCHAR(255) PRIMARY KEY, " +
                    "    NAME VARCHAR(1024) NOT NULL, " +
                    "    CONTENT_TYPE VARCHAR(255), " +
                    "    SIZE BIGINT DEFAULT 0, " +
                    "    UPLOAD_DATE TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "    STATUS VARCHAR(50) DEFAULT 'PENDING', " +
                    "    EXTRACTED_TEXT CLOB, " +
                    "    CONTENT BLOB" +
                    ")"
            );

            logger.info("DOCUMENTS table ready");

        } catch (SQLException e) {
            logger.error("Failed to initialize DocumentStore", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    /**
     * Retrieves all documents (metadata only, no binary content).
     */
    public List<Document> getAllDocuments() {
        List<Document> documents = new ArrayList<>();
        String sql = "SELECT ID, NAME, CONTENT_TYPE, SIZE, UPLOAD_DATE, STATUS, EXTRACTED_TEXT FROM DOCUMENTS ORDER BY UPLOAD_DATE DESC";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                documents.add(mapRow(rs));
            }

        } catch (SQLException e) {
            logger.error("Error retrieving all documents", e);
            throw new RuntimeException("Failed to retrieve documents", e);
        }

        return documents;
    }

    /**
     * Retrieves a single document by ID (metadata only).
     */
    public Document getDocument(String id) {
        String sql = "SELECT ID, NAME, CONTENT_TYPE, SIZE, UPLOAD_DATE, STATUS, EXTRACTED_TEXT FROM DOCUMENTS WHERE ID = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }

        } catch (SQLException e) {
            logger.error("Error retrieving document {}", id, e);
            throw new RuntimeException("Failed to retrieve document", e);
        }

        return null;
    }

    /**
     * Saves a new document with its binary content.
     */
    public void saveDocument(Document document, byte[] content) {
        String sql = "INSERT INTO DOCUMENTS (ID, NAME, CONTENT_TYPE, SIZE, UPLOAD_DATE, STATUS, CONTENT) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, document.getId());
            ps.setString(2, document.getName());
            ps.setString(3, document.getContentType());
            ps.setLong(4, document.getSize());
            ps.setTimestamp(5, new Timestamp(document.getUploadDate() != null
                    ? document.getUploadDate().getTime()
                    : System.currentTimeMillis()));
            ps.setString(6, document.getStatus());
            ps.setBytes(7, content);

            ps.executeUpdate();
            logger.info("Document saved: {} ({})", document.getName(), document.getId());

        } catch (SQLException e) {
            logger.error("Error saving document {}", document.getId(), e);
            throw new RuntimeException("Failed to save document", e);
        }
    }

    /**
     * Deletes a document by ID.
     */
    public void deleteDocument(String id) {
        String sql = "DELETE FROM DOCUMENTS WHERE ID = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, id);
            int deleted = ps.executeUpdate();

            if (deleted > 0) {
                logger.info("Document deleted: {}", id);
            } else {
                logger.warn("No document found to delete with ID: {}", id);
            }

        } catch (SQLException e) {
            logger.error("Error deleting document {}", id, e);
            throw new RuntimeException("Failed to delete document", e);
        }
    }

    /**
     * Updates the processing status of a document.
     */
    public void updateDocumentStatus(String id, String status) {
        String sql = "UPDATE DOCUMENTS SET STATUS = ? WHERE ID = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status);
            ps.setString(2, id);
            ps.executeUpdate();

            logger.info("Document {} status updated to {}", id, status);

        } catch (SQLException e) {
            logger.error("Error updating status for document {}", id, e);
            throw new RuntimeException("Failed to update document status", e);
        }
    }

    /**
     * Updates the extracted text for a document (called after Tika processing).
     */
    public void updateExtractedText(String id, String extractedText) {
        String sql = "UPDATE DOCUMENTS SET EXTRACTED_TEXT = ?, STATUS = 'PROCESSED' WHERE ID = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, extractedText);
            ps.setString(2, id);
            ps.executeUpdate();

            logger.info("Document {} extracted text updated, status set to PROCESSED", id);

        } catch (SQLException e) {
            logger.error("Error updating extracted text for document {}", id, e);
            throw new RuntimeException("Failed to update extracted text", e);
        }
    }

    /**
     * Maps a ResultSet row to a Document object.
     */
    private Document mapRow(ResultSet rs) throws SQLException {
        Document doc = new Document();
        doc.setId(rs.getString("ID"));
        doc.setName(rs.getString("NAME"));
        doc.setContentType(rs.getString("CONTENT_TYPE"));
        doc.setSize(rs.getLong("SIZE"));

        Timestamp uploadDate = rs.getTimestamp("UPLOAD_DATE");
        doc.setUploadDate(uploadDate != null ? new Date(uploadDate.getTime()) : null);

        doc.setStatus(rs.getString("STATUS"));
        doc.setExtractedText(rs.getString("EXTRACTED_TEXT"));
        return doc;
    }

    /**
     * Gets a new JDBC connection to the H2 database.
     */
    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }
}
