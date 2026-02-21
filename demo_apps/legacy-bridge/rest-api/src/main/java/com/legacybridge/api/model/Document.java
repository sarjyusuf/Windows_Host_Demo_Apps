package com.legacybridge.api.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Date;

/**
 * Document model POJO for the REST API.
 * Represents a document stored in the LegacyBridge system.
 * Uses Jackson annotations for JSON serialization.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Document {

    private String id;
    private String name;
    private String contentType;
    private long size;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    private Date uploadDate;

    private String status;
    private String extractedText;

    public Document() {
    }

    public Document(String id, String name, String contentType, long size, Date uploadDate, String status) {
        this.id = id;
        this.name = name;
        this.contentType = contentType;
        this.size = size;
        this.uploadDate = uploadDate;
        this.status = status;
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

    public Date getUploadDate() {
        return uploadDate;
    }

    public void setUploadDate(Date uploadDate) {
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
                ", status='" + status + '\'' +
                '}';
    }
}
