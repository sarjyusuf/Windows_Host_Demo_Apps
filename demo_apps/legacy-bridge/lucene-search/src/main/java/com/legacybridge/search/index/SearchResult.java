package com.legacybridge.search.index;

/**
 * POJO representing a single search result from the Lucene index.
 * Contains the document ID, name, relevance score, and a text snippet.
 */
public class SearchResult {

    private String id;
    private String name;
    private float score;
    private String snippet;

    /**
     * Default constructor for serialization.
     */
    public SearchResult() {
    }

    /**
     * Creates a new SearchResult.
     *
     * @param id      the document ID
     * @param name    the document name
     * @param score   the Lucene relevance score
     * @param snippet a text snippet from the matched document
     */
    public SearchResult(String id, String name, float score, String snippet) {
        this.id = id;
        this.name = name;
        this.score = score;
        this.snippet = snippet;
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
                ", snippet='" + (snippet != null && snippet.length() > 50
                        ? snippet.substring(0, 50) + "..." : snippet) + '\'' +
                '}';
    }
}
