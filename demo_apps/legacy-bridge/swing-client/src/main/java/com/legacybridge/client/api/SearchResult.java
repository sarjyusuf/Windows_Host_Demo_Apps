package com.legacybridge.client.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * POJO representing a search result returned by the REST API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SearchResult {

    private String id;
    private String name;
    private float score;
    private String snippet;

    public SearchResult() {
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

    public float getScore() {
        return score;
    }

    public void setScore(float score) {
        this.score = score;
    }

    public String getSnippet() {
        return snippet;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }

    @Override
    public String toString() {
        return "SearchResult{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", score=" + score +
                ", snippet='" + snippet + '\'' +
                '}';
    }
}
