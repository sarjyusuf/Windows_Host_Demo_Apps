package com.legacybridge.api.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legacybridge.api.model.ApiResponse;
import com.legacybridge.api.model.Document;
import com.legacybridge.api.service.DocumentStore;
import com.legacybridge.api.service.JmsService;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JAX-RS resource for document CRUD operations.
 * Provides endpoints for listing, uploading, retrieving, and deleting documents.
 * Documents are stored in H2 via DocumentStore.
 * On upload, a JMS message is sent to the "document.process" queue for async
 * processing by the Tika processor.
 */
@Path("/documents")
@Produces(MediaType.APPLICATION_JSON)
public class DocumentResource {

    private static final Logger logger = LoggerFactory.getLogger(DocumentResource.class);

    private final DocumentStore documentStore = DocumentStore.getInstance();
    private final JmsService jmsService = JmsService.getInstance();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * GET /documents - List all documents.
     */
    @GET
    public Response listDocuments() {
        logger.info("GET /documents - listing all documents");

        try {
            List<Document> documents = documentStore.getAllDocuments();
            logger.info("Returning {} documents", documents.size());
            return Response.ok(documents).build();
        } catch (Exception e) {
            logger.error("Error listing documents", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to list documents: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * GET /documents/{id} - Get a single document by ID.
     */
    @GET
    @Path("/{id}")
    public Response getDocument(@PathParam("id") String id) {
        logger.info("GET /documents/{} - retrieving document", id);

        try {
            Document document = documentStore.getDocument(id);
            if (document == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(ApiResponse.error("Document not found: " + id))
                        .build();
            }
            return Response.ok(document).build();
        } catch (Exception e) {
            logger.error("Error retrieving document {}", id, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to retrieve document: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * POST /documents - Upload a new document via multipart form data.
     * Stores the document metadata and binary content in H2, then sends
     * a JMS message to the "document.process" queue for async Tika processing.
     */
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response uploadDocument(
            @FormDataParam("file") InputStream fileInputStream,
            @FormDataParam("file") FormDataContentDisposition fileDisposition,
            @FormDataParam("name") String name) {

        String fileName = name;
        if (fileName == null || fileName.trim().isEmpty()) {
            fileName = fileDisposition != null ? fileDisposition.getFileName() : "unnamed";
        }

        logger.info("POST /documents - uploading document: {}", fileName);

        try {
            // Read the file content
            byte[] content = readInputStream(fileInputStream);

            // Determine content type from file extension
            String contentType = guessContentType(fileName);

            // Generate document ID
            String documentId = UUID.randomUUID().toString();

            // Create document
            Document document = new Document();
            document.setId(documentId);
            document.setName(fileName);
            document.setContentType(contentType);
            document.setSize(content.length);
            document.setUploadDate(new Date());
            document.setStatus("PENDING");

            // Store in H2
            documentStore.saveDocument(document, content);
            logger.info("Document saved to store: {} (ID: {}, size: {} bytes)",
                    fileName, documentId, content.length);

            // Send JMS message for async processing by Tika
            try {
                Map<String, Object> jmsPayload = new HashMap<>();
                jmsPayload.put("documentId", documentId);
                jmsPayload.put("fileName", fileName);
                jmsPayload.put("contentType", contentType);
                jmsPayload.put("size", content.length);

                String messageBody = objectMapper.writeValueAsString(jmsPayload);
                jmsService.sendMessage("document.process", messageBody);
                logger.info("JMS message sent for document processing: {}", documentId);
            } catch (Exception jmsEx) {
                logger.warn("Failed to send JMS message for document {}. " +
                        "Document saved but async processing may not occur.", documentId, jmsEx);
            }

            return Response.status(Response.Status.CREATED)
                    .entity(ApiResponse.ok("Document uploaded successfully", document))
                    .build();

        } catch (Exception e) {
            logger.error("Error uploading document '{}'", fileName, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to upload document: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * DELETE /documents/{id} - Delete a document by ID.
     */
    @DELETE
    @Path("/{id}")
    public Response deleteDocument(@PathParam("id") String id) {
        logger.info("DELETE /documents/{}", id);

        try {
            Document existing = documentStore.getDocument(id);
            if (existing == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(ApiResponse.error("Document not found: " + id))
                        .build();
            }

            documentStore.deleteDocument(id);
            logger.info("Document deleted: {}", id);
            return Response.ok(ApiResponse.ok("Document deleted successfully")).build();

        } catch (Exception e) {
            logger.error("Error deleting document {}", id, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to delete document: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * PUT /documents/{id}/status - Update a document's processing status.
     */
    @PUT
    @Path("/{id}/status")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateStatus(@PathParam("id") String id, Map<String, String> body) {
        String newStatus = body != null ? body.get("status") : null;
        logger.info("PUT /documents/{}/status - new status: {}", id, newStatus);

        if (newStatus == null || newStatus.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Status is required"))
                    .build();
        }

        try {
            Document existing = documentStore.getDocument(id);
            if (existing == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(ApiResponse.error("Document not found: " + id))
                        .build();
            }

            documentStore.updateDocumentStatus(id, newStatus);
            existing.setStatus(newStatus);
            logger.info("Document {} status updated to {}", id, newStatus);
            return Response.ok(ApiResponse.ok("Status updated", existing)).build();

        } catch (Exception e) {
            logger.error("Error updating status for document {}", id, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to update status: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Reads an InputStream fully into a byte array.
     */
    private byte[] readInputStream(InputStream inputStream) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int bytesRead;
        while ((bytesRead = inputStream.read(chunk)) != -1) {
            buffer.write(chunk, 0, bytesRead);
        }
        return buffer.toByteArray();
    }

    /**
     * Guesses the MIME content type based on file extension.
     */
    private String guessContentType(String fileName) {
        if (fileName == null) return "application/octet-stream";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".doc")) return "application/msword";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lower.endsWith(".xls")) return "application/vnd.ms-excel";
        if (lower.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".xml")) return "application/xml";
        if (lower.endsWith(".csv")) return "text/csv";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html";
        return "application/octet-stream";
    }
}
