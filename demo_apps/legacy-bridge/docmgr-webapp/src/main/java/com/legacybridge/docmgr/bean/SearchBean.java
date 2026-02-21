package com.legacybridge.docmgr.bean;

import com.legacybridge.docmgr.model.Document;
import com.legacybridge.docmgr.service.RestApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * CDI ViewScoped bean that backs the search.xhtml page.
 * Provides full-text search functionality against the Lucene search service
 * via the REST API.
 */
@Named
@ViewScoped
public class SearchBean implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(SearchBean.class);

    @Inject
    private RestApiClient restApiClient;

    private String query;
    private List<Document> results;
    private boolean searchPerformed;

    /**
     * Executes a search query against the search service.
     */
    public void search() {
        if (query == null || query.trim().isEmpty()) {
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_WARN, "Empty Query",
                            "Please enter a search query."));
            results = new ArrayList<>();
            searchPerformed = false;
            return;
        }

        logger.info("Searching for: {}", query);
        searchPerformed = true;

        try {
            results = restApiClient.searchDocuments(query.trim());

            if (results.isEmpty()) {
                FacesContext.getCurrentInstance().addMessage(null,
                        new FacesMessage(FacesMessage.SEVERITY_INFO, "No Results",
                                "No documents found matching '" + query + "'."));
            } else {
                FacesContext.getCurrentInstance().addMessage(null,
                        new FacesMessage(FacesMessage.SEVERITY_INFO, "Search Complete",
                                "Found " + results.size() + " result(s) for '" + query + "'."));
            }
        } catch (Exception e) {
            logger.error("Error performing search for query: {}", query, e);
            results = new ArrayList<>();
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, "Search Error",
                            "An error occurred while searching. Please try again."));
        }
    }

    // --- Getters and Setters ---

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public List<Document> getResults() {
        return results;
    }

    public void setResults(List<Document> results) {
        this.results = results;
    }

    public boolean isSearchPerformed() {
        return searchPerformed;
    }

    public void setSearchPerformed(boolean searchPerformed) {
        this.searchPerformed = searchPerformed;
    }
}
