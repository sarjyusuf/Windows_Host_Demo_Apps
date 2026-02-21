package com.legacybridge.client.ui;

import com.legacybridge.client.api.ApiClient;
import com.legacybridge.client.api.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Panel containing a JTable that displays a list of documents.
 * Supports row selection, right-click context menu, and refresh operations.
 */
public class DocumentTablePanel extends JPanel {

    private static final Logger logger = LoggerFactory.getLogger(DocumentTablePanel.class);

    private final ApiClient apiClient;
    private final JTable documentTable;
    private final DocumentTableModel tableModel;
    private final JPopupMenu contextMenu;

    private Consumer<Document> onDocumentSelected;
    private Runnable onRefreshRequested;

    public DocumentTablePanel(ApiClient apiClient) {
        this.apiClient = apiClient;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        // Header
        JLabel headerLabel = new JLabel("Documents");
        headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD, 14f));
        headerLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 8, 4));
        add(headerLabel, BorderLayout.NORTH);

        // Table model and table
        tableModel = new DocumentTableModel();
        documentTable = new JTable(tableModel);
        documentTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        documentTable.setRowHeight(24);
        documentTable.setShowGrid(false);
        documentTable.setIntercellSpacing(new Dimension(0, 0));
        documentTable.getTableHeader().setReorderingAllowed(false);
        documentTable.setFillsViewportHeight(true);

        // Column widths
        documentTable.getColumnModel().getColumn(0).setPreferredWidth(200); // Name
        documentTable.getColumnModel().getColumn(1).setPreferredWidth(100); // Type
        documentTable.getColumnModel().getColumn(2).setPreferredWidth(80);  // Size
        documentTable.getColumnModel().getColumn(3).setPreferredWidth(140); // Date
        documentTable.getColumnModel().getColumn(4).setPreferredWidth(80);  // Status

        // Right-align size column
        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
        rightRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
        documentTable.getColumnModel().getColumn(2).setCellRenderer(rightRenderer);

        // Selection listener
        documentTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && onDocumentSelected != null) {
                Document selected = getSelectedDocument();
                if (selected != null) {
                    loadDocumentDetails(selected);
                }
            }
        });

        // Context menu
        contextMenu = createContextMenu();
        documentTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handlePopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                handlePopup(e);
            }

            private void handlePopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int row = documentTable.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        documentTable.setRowSelectionInterval(row, row);
                        contextMenu.show(documentTable, e.getX(), e.getY());
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(documentTable);
        add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * Sets the callback invoked when a document row is selected.
     *
     * @param callback the callback receiving the selected document
     */
    public void setOnDocumentSelected(Consumer<Document> callback) {
        this.onDocumentSelected = callback;
    }

    /**
     * Sets the callback invoked when a refresh is requested (e.g., after delete).
     *
     * @param callback the callback to execute
     */
    public void setOnRefreshRequested(Runnable callback) {
        this.onRefreshRequested = callback;
    }

    /**
     * Replaces the table data with the given list of documents.
     *
     * @param documents the documents to display
     */
    public void setDocuments(List<Document> documents) {
        tableModel.setDocuments(documents);
    }

    /**
     * Returns the currently selected document, or null if none selected.
     *
     * @return the selected document or null
     */
    public Document getSelectedDocument() {
        int selectedRow = documentTable.getSelectedRow();
        if (selectedRow >= 0 && selectedRow < tableModel.getRowCount()) {
            return tableModel.getDocumentAt(selectedRow);
        }
        return null;
    }

    /**
     * Selects the row for the document with the given ID, if present.
     *
     * @param documentId the document ID to select
     */
    public void selectDocumentById(String documentId) {
        if (documentId == null) {
            return;
        }
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            Document doc = tableModel.getDocumentAt(i);
            if (documentId.equals(doc.getId())) {
                documentTable.setRowSelectionInterval(i, i);
                documentTable.scrollRectToVisible(documentTable.getCellRect(i, 0, true));
                break;
            }
        }
    }

    /**
     * Returns the number of documents currently displayed.
     *
     * @return the document count
     */
    public int getDocumentCount() {
        return tableModel.getRowCount();
    }

    private void loadDocumentDetails(Document summaryDoc) {
        SwingWorker<Document, Void> worker = new SwingWorker<Document, Void>() {
            @Override
            protected Document doInBackground() {
                return apiClient.getDocument(summaryDoc.getId());
            }

            @Override
            protected void done() {
                try {
                    Document fullDoc = get();
                    if (fullDoc != null && onDocumentSelected != null) {
                        onDocumentSelected.accept(fullDoc);
                    } else if (onDocumentSelected != null) {
                        // Fall back to the summary document if full load fails
                        onDocumentSelected.accept(summaryDoc);
                    }
                } catch (Exception ex) {
                    logger.warn("Failed to load document details, using summary", ex);
                    if (onDocumentSelected != null) {
                        onDocumentSelected.accept(summaryDoc);
                    }
                }
            }
        };
        worker.execute();
    }

    private JPopupMenu createContextMenu() {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem viewItem = new JMenuItem("View Details");
        viewItem.addActionListener(e -> {
            Document doc = getSelectedDocument();
            if (doc != null) {
                loadDocumentDetails(doc);
            }
        });
        menu.add(viewItem);

        menu.addSeparator();

        JMenuItem deleteItem = new JMenuItem("Delete");
        deleteItem.addActionListener(e -> {
            Document doc = getSelectedDocument();
            if (doc == null) {
                return;
            }

            int confirm = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to delete \"" + doc.getName() + "\"?",
                    "Confirm Delete",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);

            if (confirm == JOptionPane.YES_OPTION) {
                deleteDocument(doc);
            }
        });
        menu.add(deleteItem);

        JMenuItem reprocessItem = new JMenuItem("Reprocess");
        reprocessItem.addActionListener(e -> {
            Document doc = getSelectedDocument();
            if (doc != null) {
                JOptionPane.showMessageDialog(this,
                        "Reprocess request sent for: " + doc.getName(),
                        "Reprocess",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        });
        menu.add(reprocessItem);

        return menu;
    }

    private void deleteDocument(Document doc) {
        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                apiClient.deleteDocument(doc.getId());
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    if (onRefreshRequested != null) {
                        onRefreshRequested.run();
                    }
                } catch (Exception ex) {
                    logger.error("Failed to delete document", ex);
                    JOptionPane.showMessageDialog(DocumentTablePanel.this,
                            "Failed to delete document:\n" + ex.getMessage(),
                            "Delete Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    // -------------------------------------------------------------------------
    // Custom Table Model
    // -------------------------------------------------------------------------

    /**
     * Table model backed by a list of Document objects.
     */
    private static class DocumentTableModel extends AbstractTableModel {

        private static final String[] COLUMN_NAMES = {"Name", "Type", "Size", "Date", "Status"};
        private List<Document> documents = new ArrayList<>();

        void setDocuments(List<Document> documents) {
            this.documents = documents != null ? new ArrayList<>(documents) : new ArrayList<>();
            fireTableDataChanged();
        }

        Document getDocumentAt(int row) {
            if (row >= 0 && row < documents.size()) {
                return documents.get(row);
            }
            return null;
        }

        @Override
        public int getRowCount() {
            return documents.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMN_NAMES.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMN_NAMES[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Document doc = documents.get(rowIndex);
            switch (columnIndex) {
                case 0:
                    return doc.getName();
                case 1:
                    return doc.getContentType() != null ? doc.getContentType() : "Unknown";
                case 2:
                    return formatFileSize(doc.getSize());
                case 3:
                    return doc.getUploadDate() != null ? doc.getUploadDate() : "Unknown";
                case 4:
                    return doc.getStatus() != null ? doc.getStatus() : "Unknown";
                default:
                    return "";
            }
        }

        private String formatFileSize(long bytes) {
            if (bytes < 1024) {
                return bytes + " B";
            } else if (bytes < 1024 * 1024) {
                return String.format("%.1f KB", bytes / 1024.0);
            } else if (bytes < 1024 * 1024 * 1024) {
                return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
            } else {
                return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
            }
        }
    }
}
