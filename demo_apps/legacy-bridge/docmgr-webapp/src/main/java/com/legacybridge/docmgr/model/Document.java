package com.legacybridge.docmgr.model;

import java.io.Serializable;
import java.util.Date;

/**
 * Document POJO representing a document in the LegacyBridge system.
 * Used as the transfer object between the UI and the REST API.
 */
public class Document implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private String contentType;
    private long size;
    private Date uploadDate;
    private String status;
    private String content;

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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
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
