package com.legacybridge.client;

import com.formdev.flatlaf.FlatIntelliJLaf;
import com.legacybridge.client.api.ApiClient;
import com.legacybridge.client.api.Document;
import com.legacybridge.client.ui.DocumentDetailPanel;
import com.legacybridge.client.ui.DocumentTablePanel;
import com.legacybridge.client.ui.SearchDialog;
import com.legacybridge.client.ui.StatusBar;
import com.legacybridge.client.ui.UploadDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

/**
 * Main entry point for the LegacyBridge Document Manager desktop client.
 * Sets up the FlatLaf IntelliJ look and feel, creates the main JFrame with
 * a toolbar, split pane (document list + detail view), and status bar.
 * Periodically refreshes the document list from the REST API.
 */
public class LegacyBridgeClient extends JFrame {

    private static final Logger logger = LoggerFactory.getLogger(LegacyBridgeClient.class);

    private static final String TITLE = "LegacyBridge Document Manager";
    private static final int WIDTH = 1000;
    private static final int HEIGHT = 700;
    private static final int REFRESH_INTERVAL_MS = 30_000;

    private final ApiClient apiClient;
    private final DocumentTablePanel documentTablePanel;
    private final DocumentDetailPanel documentDetailPanel;
    private final StatusBar statusBar;
    private Timer refreshTimer;

    /**
     * Constructs the main application frame.
     */
    public LegacyBridgeClient() {
        super(TITLE);
        this.apiClient = new ApiClient();

        setSize(WIDTH, HEIGHT);
        setMinimumSize(new Dimension(800, 500));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout());

        // -- Toolbar --
        JToolBar toolBar = createToolBar();
        add(toolBar, BorderLayout.NORTH);

        // -- Main content: split pane --
        documentTablePanel = new DocumentTablePanel(apiClient);
        documentDetailPanel = new DocumentDetailPanel(apiClient);

        // Wire up selection callback: when a document is selected in the table,
        // show its details in the detail panel
        documentTablePanel.setOnDocumentSelected(doc -> documentDetailPanel.showDocument(doc));

        // Wire up refresh callback from table panel (e.g., after context-menu delete)
        documentTablePanel.setOnRefreshRequested(this::refreshDocuments);

        // Wire up delete callback from detail panel
        documentDetailPanel.setOnDocumentDeleted(this::refreshDocuments);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                documentTablePanel, documentDetailPanel);
        splitPane.setDividerLocation(400);
        splitPane.setResizeWeight(0.4);
        splitPane.setContinuousLayout(true);
        add(splitPane, BorderLayout.CENTER);

        // -- Status bar --
        statusBar = new StatusBar();
        add(statusBar, BorderLayout.SOUTH);

        // -- Window close handler --
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleWindowClose();
            }
        });

        // -- Auto-refresh timer --
        refreshTimer = new Timer(REFRESH_INTERVAL_MS, e -> refreshDocuments());
        refreshTimer.setRepeats(true);

        logger.info("LegacyBridge Client initialized");
    }

    /**
     * Creates the toolbar with Refresh, Upload, Search, and Delete buttons.
     */
    private JToolBar createToolBar() {
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.setBorderPainted(true);
        toolBar.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        JButton refreshButton = new JButton("Refresh");
        refreshButton.setToolTipText("Refresh document list");
        refreshButton.setFocusable(false);
        refreshButton.addActionListener(e -> refreshDocuments());
        toolBar.add(refreshButton);

        toolBar.addSeparator(new Dimension(8, 0));

        JButton uploadButton = new JButton("Upload");
        uploadButton.setToolTipText("Upload a new document");
        uploadButton.setFocusable(false);
        uploadButton.addActionListener(e -> showUploadDialog());
        toolBar.add(uploadButton);

        toolBar.addSeparator(new Dimension(8, 0));

        JButton searchButton = new JButton("Search");
        searchButton.setToolTipText("Search documents");
        searchButton.setFocusable(false);
        searchButton.addActionListener(e -> showSearchDialog());
        toolBar.add(searchButton);

        toolBar.addSeparator(new Dimension(8, 0));

        JButton deleteButton = new JButton("Delete");
        deleteButton.setToolTipText("Delete selected document");
        deleteButton.setFocusable(false);
        deleteButton.addActionListener(e -> deleteSelectedDocument());
        toolBar.add(deleteButton);

        return toolBar;
    }

    /**
     * Refreshes the document list from the REST API using a background thread.
     */
    private void refreshDocuments() {
        SwingWorker<List<Document>, Void> worker = new SwingWorker<List<Document>, Void>() {
            private boolean connected;

            @Override
            protected List<Document> doInBackground() {
                connected = apiClient.checkHealth();
                return apiClient.getAllDocuments();
            }

            @Override
            protected void done() {
                try {
                    List<Document> documents = get();
                    documentTablePanel.setDocuments(documents);
                    statusBar.setConnected(connected);
                    statusBar.setDocumentCount(documents.size());
                    statusBar.updateRefreshTime();
                    logger.debug("Document list refreshed: {} documents, connected={}", documents.size(), connected);
                } catch (Exception ex) {
                    logger.error("Failed to refresh document list", ex);
                    statusBar.setConnected(false);
                    statusBar.updateRefreshTime();
                }
            }
        };
        worker.execute();
    }

    /**
     * Opens the upload dialog and refreshes the list if a document was uploaded.
     */
    private void showUploadDialog() {
        UploadDialog dialog = new UploadDialog(this, apiClient);
        dialog.setVisible(true);
        if (dialog.isUploaded()) {
            refreshDocuments();
        }
    }

    /**
     * Opens the search dialog. If a result is selected, highlights it in the table.
     */
    private void showSearchDialog() {
        SearchDialog dialog = new SearchDialog(this, apiClient);
        dialog.setOnResultSelected(documentId -> {
            // Refresh the list first, then select the document
            SwingWorker<List<Document>, Void> worker = new SwingWorker<List<Document>, Void>() {
                @Override
                protected List<Document> doInBackground() {
                    return apiClient.getAllDocuments();
                }

                @Override
                protected void done() {
                    try {
                        List<Document> documents = get();
                        documentTablePanel.setDocuments(documents);
                        statusBar.setDocumentCount(documents.size());
                        statusBar.updateRefreshTime();
                        documentTablePanel.selectDocumentById(documentId);
                    } catch (Exception ex) {
                        logger.error("Failed to refresh after search selection", ex);
                    }
                }
            };
            worker.execute();
        });
        dialog.setVisible(true);
    }

    /**
     * Deletes the document currently selected in the table.
     */
    private void deleteSelectedDocument() {
        Document selected = documentTablePanel.getSelectedDocument();
        if (selected == null) {
            JOptionPane.showMessageDialog(this,
                    "Please select a document to delete.",
                    "No Selection",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to delete \"" + selected.getName() + "\"?",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                apiClient.deleteDocument(selected.getId());
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    documentDetailPanel.clear();
                    refreshDocuments();
                } catch (Exception ex) {
                    logger.error("Failed to delete document", ex);
                    JOptionPane.showMessageDialog(LegacyBridgeClient.this,
                            "Failed to delete document:\n" + ex.getMessage(),
                            "Delete Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    /**
     * Handles the window close event: confirms exit, stops the timer, cleans up.
     */
    private void handleWindowClose() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to exit?",
                "Confirm Exit",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            logger.info("Application shutting down");
            if (refreshTimer != null) {
                refreshTimer.stop();
            }
            apiClient.close();
            dispose();
            System.exit(0);
        }
    }

    /**
     * Starts the application: performs initial data load and starts the auto-refresh timer.
     */
    public void start() {
        setVisible(true);
        refreshDocuments();
        refreshTimer.start();
        logger.info("Application started, auto-refresh every {} ms", REFRESH_INTERVAL_MS);
    }

    /**
     * Main entry point. Sets up FlatLaf look and feel and launches the client.
     */
    public static void main(String[] args) {
        // Set up FlatLaf IntelliJ theme
        try {
            FlatIntelliJLaf.setup();
            UIManager.put("TextComponent.arc", 5);
            UIManager.put("Component.arc", 5);
            UIManager.put("Button.arc", 5);
            logger.info("FlatLaf IntelliJ theme applied");
        } catch (Exception e) {
            logger.warn("Failed to set FlatLaf look and feel, falling back to system L&F", e);
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ex) {
                logger.warn("Failed to set system look and feel", ex);
            }
        }

        // Launch on the EDT
        SwingUtilities.invokeLater(() -> {
            LegacyBridgeClient client = new LegacyBridgeClient();
            client.start();
        });
    }
}
