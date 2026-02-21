package com.legacybridge.client.ui;

import com.legacybridge.client.api.ApiClient;
import com.legacybridge.client.api.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Dialog for searching documents. Provides a search text field and a results table.
 * Double-clicking a result invokes a callback to select that document in the main table.
 */
public class SearchDialog extends JDialog {

    private static final Logger logger = LoggerFactory.getLogger(SearchDialog.class);

    private final ApiClient apiClient;
    private final JTextField searchField;
    private final JButton searchButton;
    private final JTable resultsTable;
    private final SearchResultTableModel tableModel;
    private final JLabel statusLabel;

    private Consumer<String> onResultSelected;

    /**
     * Creates a search dialog.
     *
     * @param owner     the parent frame
     * @param apiClient the API client to use for searching
     */
    public SearchDialog(Frame owner, ApiClient apiClient) {
        super(owner, "Search Documents", true);
        this.apiClient = apiClient;

        setSize(600, 450);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel contentPanel = new JPanel(new BorderLayout(8, 8));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        // Search input row
        JPanel searchPanel = new JPanel(new BorderLayout(8, 0));
        JLabel searchLabel = new JLabel("Search:");
        searchField = new JTextField();
        searchButton = new JButton("Search");
        searchButton.addActionListener(e -> performSearch());

        searchPanel.add(searchLabel, BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(searchButton, BorderLayout.EAST);
        contentPanel.add(searchPanel, BorderLayout.NORTH);

        // Enter key triggers search
        searchField.addActionListener(e -> performSearch());

        // Results table
        tableModel = new SearchResultTableModel();
        resultsTable = new JTable(tableModel);
        resultsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultsTable.setRowHeight(24);
        resultsTable.setShowGrid(false);
        resultsTable.setFillsViewportHeight(true);

        resultsTable.getColumnModel().getColumn(0).setPreferredWidth(250); // Name
        resultsTable.getColumnModel().getColumn(1).setPreferredWidth(60);  // Score
        resultsTable.getColumnModel().getColumn(2).setPreferredWidth(290); // Snippet

        // Double-click to select a result
        resultsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = resultsTable.getSelectedRow();
                    if (row >= 0) {
                        SearchResult result = tableModel.getResultAt(row);
                        if (result != null && onResultSelected != null) {
                            onResultSelected.accept(result.getId());
                            dispose();
                        }
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(resultsTable);
        contentPanel.add(scrollPane, BorderLayout.CENTER);

        // Status and buttons
        JPanel bottomPanel = new JPanel(new BorderLayout());
        statusLabel = new JLabel("Enter a search query and press Search.");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC, 12f));
        bottomPanel.add(statusLabel, BorderLayout.WEST);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        JButton selectButton = new JButton("Select");
        selectButton.addActionListener(e -> {
            int row = resultsTable.getSelectedRow();
            if (row >= 0) {
                SearchResult result = tableModel.getResultAt(row);
                if (result != null && onResultSelected != null) {
                    onResultSelected.accept(result.getId());
                    dispose();
                }
            } else {
                JOptionPane.showMessageDialog(this,
                        "Please select a result from the table.",
                        "No Selection",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        });

        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());

        buttonPanel.add(selectButton);
        buttonPanel.add(closeButton);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);

        contentPanel.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(contentPanel);

        // Focus the search field on open
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(java.awt.event.WindowEvent e) {
                searchField.requestFocusInWindow();
            }
        });
    }

    /**
     * Sets the callback invoked when a search result is selected (double-clicked).
     * The callback receives the document ID.
     *
     * @param callback the callback to execute
     */
    public void setOnResultSelected(Consumer<String> callback) {
        this.onResultSelected = callback;
    }

    private void performSearch() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please enter a search query.",
                    "Empty Query",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        searchButton.setEnabled(false);
        searchField.setEnabled(false);
        statusLabel.setText("Searching...");

        SwingWorker<List<SearchResult>, Void> worker = new SwingWorker<List<SearchResult>, Void>() {
            @Override
            protected List<SearchResult> doInBackground() {
                return apiClient.searchDocuments(query);
            }

            @Override
            protected void done() {
                searchButton.setEnabled(true);
                searchField.setEnabled(true);
                try {
                    List<SearchResult> results = get();
                    tableModel.setResults(results);
                    if (results.isEmpty()) {
                        statusLabel.setText("No results found for \"" + query + "\".");
                    } else {
                        statusLabel.setText(results.size() + " result" + (results.size() != 1 ? "s" : "") + " found.");
                    }
                } catch (Exception ex) {
                    logger.error("Search failed", ex);
                    statusLabel.setText("Search failed.");
                    JOptionPane.showMessageDialog(SearchDialog.this,
                            "Search failed:\n" + ex.getMessage(),
                            "Search Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    // -------------------------------------------------------------------------
    // Search Result Table Model
    // -------------------------------------------------------------------------

    private static class SearchResultTableModel extends AbstractTableModel {

        private static final String[] COLUMN_NAMES = {"Name", "Score", "Snippet"};
        private List<SearchResult> results = new ArrayList<>();

        void setResults(List<SearchResult> results) {
            this.results = results != null ? new ArrayList<>(results) : new ArrayList<>();
            fireTableDataChanged();
        }

        SearchResult getResultAt(int row) {
            if (row >= 0 && row < results.size()) {
                return results.get(row);
            }
            return null;
        }

        @Override
        public int getRowCount() {
            return results.size();
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
            SearchResult result = results.get(rowIndex);
            switch (columnIndex) {
                case 0:
                    return result.getName();
                case 1:
                    return String.format("%.2f", result.getScore());
                case 2:
                    return result.getSnippet() != null ? result.getSnippet() : "";
                default:
                    return "";
            }
        }
    }
}
