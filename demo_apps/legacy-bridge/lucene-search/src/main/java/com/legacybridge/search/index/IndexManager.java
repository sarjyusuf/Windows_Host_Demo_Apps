package com.legacybridge.search.index;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages the Lucene index for full-text document search.
 * Provides operations for indexing, searching, deleting documents,
 * and retrieving index statistics.
 *
 * Uses FSDirectory for persistent storage and StandardAnalyzer for text analysis.
 */
public class IndexManager {

    private static final Logger logger = LoggerFactory.getLogger(IndexManager.class);

    private final Directory directory;
    private final IndexWriter indexWriter;
    private final StandardAnalyzer analyzer;

    /**
     * Creates an IndexManager with the index stored at the given path.
     * Creates the directory if it does not exist.
     *
     * @param indexPath the file system path for the Lucene index
     * @throws IOException if the index directory cannot be created or opened
     */
    public IndexManager(Path indexPath) throws IOException {
        logger.info("Initializing IndexManager at: {}", indexPath.toAbsolutePath());

        // Ensure the index directory exists
        Files.createDirectories(indexPath);

        this.analyzer = new StandardAnalyzer();
        this.directory = FSDirectory.open(indexPath);

        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        config.setRAMBufferSizeMB(16.0);

        this.indexWriter = new IndexWriter(directory, config);

        logger.info("IndexManager initialized. Index path: {}, Document count: {}",
                indexPath.toAbsolutePath(), getDocumentCount());
    }

    /**
     * Indexes a document, adding or updating it in the Lucene index.
     * Uses the document ID as a unique key for update operations.
     *
     * @param id   the unique document identifier
     * @param name the document name/title
     * @param text the document text content to index
     * @throws IOException if the indexing operation fails
     */
    public void indexDocument(String id, String name, String text) throws IOException {
        logger.info("Indexing document - ID: {}, Name: {}, Text length: {} chars", id, name, text.length());
        long startTime = System.currentTimeMillis();

        Document doc = new Document();
        // StringField is not tokenized, used for exact matching (ID lookups)
        doc.add(new StringField("id", id, Field.Store.YES));
        // TextField is tokenized, used for full-text search
        doc.add(new TextField("name", name, Field.Store.YES));
        doc.add(new TextField("text", text, Field.Store.YES));

        // Update (delete + add) to handle re-indexing of existing documents
        indexWriter.updateDocument(new Term("id", id), doc);
        indexWriter.commit();

        long elapsed = System.currentTimeMillis() - startTime;
        logger.info("Document indexed successfully in {} ms - ID: {}, Total docs: {}",
                elapsed, id, getDocumentCount());
    }

    /**
     * Deletes a document from the Lucene index by its ID.
     *
     * @param id the unique document identifier to delete
     * @throws IOException if the delete operation fails
     */
    public void deleteDocument(String id) throws IOException {
        logger.info("Deleting document from index - ID: {}", id);
        long startTime = System.currentTimeMillis();

        indexWriter.deleteDocuments(new Term("id", id));
        indexWriter.commit();

        long elapsed = System.currentTimeMillis() - startTime;
        logger.info("Document deleted in {} ms - ID: {}, Remaining docs: {}", elapsed, id, getDocumentCount());
    }

    /**
     * Searches the Lucene index for documents matching the given query string.
     * Searches across both "text" and "name" fields using StandardAnalyzer.
     *
     * @param queryStr   the search query string
     * @param maxResults the maximum number of results to return
     * @return a list of SearchResult objects matching the query
     * @throws IOException if the search operation fails
     */
    public List<SearchResult> search(String queryStr, int maxResults) throws IOException {
        logger.info("Searching for: '{}' (max results: {})", queryStr, maxResults);
        long startTime = System.currentTimeMillis();

        List<SearchResult> results = new ArrayList<>();

        try {
            IndexReader reader = DirectoryReader.open(indexWriter);
            IndexSearcher searcher = new IndexSearcher(reader);

            // Search across both "text" and "name" fields
            String[] fields = {"text", "name"};
            MultiFieldQueryParser parser = new MultiFieldQueryParser(fields, analyzer);
            Query query = parser.parse(queryStr);

            logger.debug("Parsed query: {}", query);

            TopDocs topDocs = searcher.search(query, maxResults);
            logger.debug("Found {} total hits", topDocs.totalHits.value);

            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.doc(scoreDoc.doc);
                String id = doc.get("id");
                String name = doc.get("name");
                float score = scoreDoc.score;

                // Create a snippet from the text (first 200 chars)
                String text = doc.get("text");
                String snippet = text != null && text.length() > 200
                        ? text.substring(0, 200) + "..."
                        : (text != null ? text : "");

                results.add(new SearchResult(id, name, score, snippet));
                logger.debug("  Hit: id={}, name={}, score={}", id, name, score);
            }

            reader.close();

        } catch (ParseException e) {
            logger.error("Failed to parse query '{}': {}", queryStr, e.getMessage());
            throw new IOException("Invalid search query: " + e.getMessage(), e);
        }

        long elapsed = System.currentTimeMillis() - startTime;
        logger.info("Search complete in {} ms. Query: '{}', Results: {}", elapsed, queryStr, results.size());

        return results;
    }

    /**
     * Returns the total number of documents in the index.
     *
     * @return the document count
     */
    public int getDocumentCount() {
        return indexWriter.getDocStats().numDocs;
    }

    /**
     * Closes the IndexWriter and releases resources.
     *
     * @throws IOException if the close operation fails
     */
    public void close() throws IOException {
        logger.info("Closing IndexManager...");
        indexWriter.close();
        directory.close();
        logger.info("IndexManager closed successfully.");
    }
}
