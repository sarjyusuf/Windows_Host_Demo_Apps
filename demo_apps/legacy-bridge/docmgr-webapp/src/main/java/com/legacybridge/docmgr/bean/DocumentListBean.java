package com.legacybridge.docmgr.bean;

import com.legacybridge.docmgr.model.Document;
import com.legacybridge.docmgr.service.RestApiClient;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.file.UploadedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * CDI ViewScoped bean that backs the index.xhtml document list page.
 * Manages the list of documents, file uploads, deletions, and quick search.
 */
@Named
@ViewScoped
public class DocumentListBean implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(DocumentListBean.class);

    @Inject
    private RestApiClient restApiClient;

    private List<Document> documents;
    private Document selectedDocument;
    private String searchQuery;

    @PostConstruct
    public void init() {
        logger.info("Initializing DocumentListBean");
        loadDocuments();
    }

    /**
     * Loads all documents from the REST API.
     */
    private void loadDocuments() {
        try {
            documents = restApiClient.getAllDocuments();
            logger.info("Loaded {} documents", documents.size());
        } catch (Exception e) {
            logger.error("Error loading documents", e);
            documents = new ArrayList<>();
            addMessage(FacesMessage.SEVERITY_ERROR, "Error",
                    "Failed to load documents from the server. Please check if the REST API is running.");
        }
    }

    /**
     * Handles file upload events from the PrimeFaces fileUpload component.
     */
    public void handleFileUpload(FileUploadEvent event) {
        UploadedFile uploadedFile = event.getFile();
        if (uploadedFile == null || uploadedFile.getContent() == null) {
            addMessage(FacesMessage.SEVERITY_WARN, "Upload Failed", "No file was received.");
            return;
        }

        String fileName = uploadedFile.getFileName();
        String contentType = uploadedFile.getContentType();
        byte[] content = uploadedFile.getContent();

        logger.info("Uploading file: {} (type: {}, size: {} bytes)", fileName, contentType, content.length);

        try {
            Document uploaded = restApiClient.uploadDocument(fileName, content, contentType);
            if (uploaded != null) {
                addMessage(FacesMessage.SEVERITY_INFO, "Upload Successful",
                        "File '" + fileName + "' has been uploaded and queued for processing.");
                loadDocuments();
            } else {
                addMessage(FacesMessage.SEVERITY_ERROR, "Upload Failed",
                        "The server could not process the file '" + fileName + "'.");
            }
        } catch (Exception e) {
            logger.error("Error uploading file '{}'", fileName, e);
            addMessage(FacesMessage.SEVERITY_ERROR, "Upload Error",
                    "An unexpected error occurred while uploading '" + fileName + "'.");
        }
    }

    /**
     * Deletes a document and refreshes the list.
     */
    public void deleteDocument(Document document) {
        if (document == null || document.getId() == null) {
            addMessage(FacesMessage.SEVERITY_WARN, "Delete Failed", "No document selected.");
            return;
        }

        logger.info("Deleting document: {} ({})", document.getName(), document.getId());

        try {
            restApiClient.deleteDocument(document.getId());
            addMessage(FacesMessage.SEVERITY_INFO, "Document Deleted",
                    "'" + document.getName() + "' has been deleted.");
            loadDocuments();
        } catch (Exception e) {
            logger.error("Error deleting document {}", document.getId(), e);
            addMessage(FacesMessage.SEVERITY_ERROR, "Delete Error",
                    "Failed to delete '" + document.getName() + "'.");
        }
    }

    /**
     * Performs a quick search using the search query field.
     */
    public void quickSearch() {
        if (searchQuery == null || searchQuery.trim().isEmpty()) {
            loadDocuments();
            return;
        }

        logger.info("Quick search for: {}", searchQuery);

        try {
            documents = restApiClient.searchDocuments(searchQuery.trim());
            if (documents.isEmpty()) {
                addMessage(FacesMessage.SEVERITY_INFO, "No Results",
                        "No documents match the query '" + searchQuery + "'.");
            } else {
                addMessage(FacesMessage.SEVERITY_INFO, "Search Results",
                        "Found " + documents.size() + " document(s) matching '" + searchQuery + "'.");
            }
        } catch (Exception e) {
            logger.error("Error performing quick search", e);
            addMessage(FacesMessage.SEVERITY_ERROR, "Search Error",
                    "An error occurred while searching.");
            loadDocuments();
        }
    }

    /**
     * Refreshes the document list from the server.
     */
    public void refresh() {
        logger.info("Refreshing document list");
        searchQuery = null;
        loadDocuments();
        addMessage(FacesMessage.SEVERITY_INFO, "Refreshed", "Document list has been refreshed.");
    }

    private void addMessage(FacesMessage.Severity severity, String summary, String detail) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(severity, summary, detail));
    }

    // --- Getters and Setters ---

    public List<Document> getDocuments() {
        return documents;
    }

    public void setDocuments(List<Document> documents) {
        this.documents = documents;
    }

    public Document getSelectedDocument() {
        return selectedDocument;
    }

    public void setSelectedDocument(Document selectedDocument) {
        this.selectedDocument = selectedDocument;
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    public void setSearchQuery(String searchQuery) {
        this.searchQuery = searchQuery;
    }
}
