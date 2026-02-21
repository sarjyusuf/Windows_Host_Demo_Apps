package com.legacybridge.client.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * POJO representing a document returned by the REST API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Document {

    private String id;
    private String name;
    private String contentType;
    private long size;
    private String uploadDate;
    private String status;
    private String extractedText;

    public Document() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getUploadDate() {
        return uploadDate;
    }

    public void setUploadDate(String uploadDate) {
        this.uploadDate = uploadDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getExtractedText() {
        return extractedText;
    }

    public void setExtractedText(String extractedText) {
        this.extractedText = extractedText;
    }

    @Override
    public String toString() {
        return "Document{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", contentType='" + contentType + '\'' +
                ", size=" + size +
                ", uploadDate='" + uploadDate + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
