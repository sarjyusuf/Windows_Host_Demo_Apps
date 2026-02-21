package com.legacybridge.client.ui;

import com.legacybridge.client.api.ApiClient;
import com.legacybridge.client.api.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

/**
 * Panel that displays the details of a selected document, including metadata
 * and extracted text content. Also provides action buttons for operations
 * on the selected document.
 */
public class DocumentDetailPanel extends JPanel {

    private static final Logger logger = LoggerFactory.getLogger(DocumentDetailPanel.class);

    private final ApiClient apiClient;

    private final JLabel titleLabel;
    private final JLabel idValueLabel;
    private final JLabel nameValueLabel;
    private final JLabel typeValueLabel;
    private final JLabel sizeValueLabel;
    private final JLabel dateValueLabel;
    private final JLabel statusValueLabel;
    private final JTextArea extractedTextArea;
    private final JButton downloadButton;
    private final JButton deleteButton;
    private final JButton reprocessButton;
    private final JPanel contentPanel;
    private final JLabel placeholderLabel;

    private Document currentDocument;
    private Runnable onDocumentDeleted;

    public DocumentDetailPanel(ApiClient apiClient) {
        this.apiClient = apiClient;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Placeholder shown when no document is selected
        placeholderLabel = new JLabel("Select a document to view details", SwingConstants.CENTER);
        placeholderLabel.setFont(placeholderLabel.getFont().deriveFont(Font.ITALIC, 14f));
        placeholderLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

        // Content panel with actual document details
        contentPanel = new JPanel(new BorderLayout(0, 10));

        // Header
        titleLabel = new JLabel("Document Details");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        contentPanel.add(titleLabel, BorderLayout.NORTH);

        // Metadata grid
        JPanel metadataPanel = new JPanel(new GridBagLayout());
        metadataPanel.setBorder(BorderFactory.createTitledBorder("Metadata"));
        GridBagConstraints labelGbc = new GridBagConstraints();
        labelGbc.anchor = GridBagConstraints.WEST;
        labelGbc.insets = new Insets(3, 6, 3, 10);
        labelGbc.gridx = 0;

        GridBagConstraints valueGbc = new GridBagConstraints();
        valueGbc.anchor = GridBagConstraints.WEST;
        valueGbc.fill = GridBagConstraints.HORIZONTAL;
        valueGbc.weightx = 1.0;
        valueGbc.insets = new Insets(3, 0, 3, 6);
        valueGbc.gridx = 1;

        idValueLabel = new JLabel();
        nameValueLabel = new JLabel();
        typeValueLabel = new JLabel();
        sizeValueLabel = new JLabel();
        dateValueLabel = new JLabel();
        statusValueLabel = new JLabel();

        int row = 0;
        addMetadataRow(metadataPanel, labelGbc, valueGbc, row++, "ID:", idValueLabel);
        addMetadataRow(metadataPanel, labelGbc, valueGbc, row++, "Name:", nameValueLabel);
        addMetadataRow(metadataPanel, labelGbc, valueGbc, row++, "Type:", typeValueLabel);
        addMetadataRow(metadataPanel, labelGbc, valueGbc, row++, "Size:", sizeValueLabel);
        addMetadataRow(metadataPanel, labelGbc, valueGbc, row++, "Date:", dateValueLabel);
        addMetadataRow(metadataPanel, labelGbc, valueGbc, row++, "Status:", statusValueLabel);

        // Extracted text
        JPanel textPanel = new JPanel(new BorderLayout());
        textPanel.setBorder(BorderFactory.createTitledBorder("Extracted Text"));

        extractedTextArea = new JTextArea();
        extractedTextArea.setEditable(false);
        extractedTextArea.setLineWrap(true);
        extractedTextArea.setWrapStyleWord(true);
        extractedTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane textScrollPane = new JScrollPane(extractedTextArea);
        textScrollPane.setPreferredSize(new Dimension(0, 200));
        textPanel.add(textScrollPane, BorderLayout.CENTER);

        // Center area: metadata on top, text filling the rest
        JPanel centerPanel = new JPanel(new BorderLayout(0, 8));
        centerPanel.add(metadataPanel, BorderLayout.NORTH);
        centerPanel.add(textPanel, BorderLayout.CENTER);
        contentPanel.add(centerPanel, BorderLayout.CENTER);

        // Action buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));

        downloadButton = new JButton("Download");
        downloadButton.setToolTipText("Download this document");
        downloadButton.addActionListener(e -> handleDownload());

        deleteButton = new JButton("Delete");
        deleteButton.setToolTipText("Delete this document");
        deleteButton.addActionListener(e -> handleDelete());

        reprocessButton = new JButton("Reprocess");
        reprocessButton.setToolTipText("Reprocess this document");
        reprocessButton.addActionListener(e -> handleReprocess());

        buttonPanel.add(downloadButton);
        buttonPanel.add(reprocessButton);
        buttonPanel.add(deleteButton);
        contentPanel.add(buttonPanel, BorderLayout.SOUTH);

        // Start with placeholder
        add(placeholderLabel, BorderLayout.CENTER);
    }

    /**
     * Sets the callback invoked when a document is deleted.
     *
     * @param callback the callback to execute
     */
    public void setOnDocumentDeleted(Runnable callback) {
        this.onDocumentDeleted = callback;
    }

    /**
     * Displays the details of the given document, or clears the view if null.
     *
     * @param document the document to display, or null to clear
     */
    public void showDocument(Document document) {
        this.currentDocument = document;
        removeAll();

        if (document == null) {
            add(placeholderLabel, BorderLayout.CENTER);
        } else {
            titleLabel.setText(document.getName());
            idValueLabel.setText(document.getId());
            nameValueLabel.setText(document.getName());
            typeValueLabel.setText(document.getContentType() != null ? document.getContentType() : "Unknown");
            sizeValueLabel.setText(formatFileSize(document.getSize()));
            dateValueLabel.setText(document.getUploadDate() != null ? document.getUploadDate() : "Unknown");
            statusValueLabel.setText(document.getStatus() != null ? document.getStatus() : "Unknown");
            extractedTextArea.setText(document.getExtractedText() != null ? document.getExtractedText() : "(No extracted text available)");
            extractedTextArea.setCaretPosition(0);
            add(contentPanel, BorderLayout.CENTER);
        }

        revalidate();
        repaint();
    }

    /**
     * Clears the detail view.
     */
    public void clear() {
        showDocument(null);
    }

    private void handleDownload() {
        if (currentDocument == null) {
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new java.io.File(currentDocument.getName()));
        int result = fileChooser.showSaveDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {
            JOptionPane.showMessageDialog(this,
                    "Download functionality would save to:\n" + fileChooser.getSelectedFile().getAbsolutePath(),
                    "Download",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void handleDelete() {
        if (currentDocument == null) {
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to delete \"" + currentDocument.getName() + "\"?",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                apiClient.deleteDocument(currentDocument.getId());
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    clear();
                    if (onDocumentDeleted != null) {
                        onDocumentDeleted.run();
                    }
                } catch (Exception ex) {
                    logger.error("Failed to delete document", ex);
                    JOptionPane.showMessageDialog(DocumentDetailPanel.this,
                            "Failed to delete document:\n" + ex.getMessage(),
                            "Delete Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void handleReprocess() {
        if (currentDocument == null) {
            return;
        }

        JOptionPane.showMessageDialog(this,
                "Reprocess request sent for: " + currentDocument.getName(),
                "Reprocess",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void addMetadataRow(JPanel panel, GridBagConstraints labelGbc,
                                GridBagConstraints valueGbc, int row,
                                String labelText, JLabel valueLabel) {
        labelGbc.gridy = row;
        JLabel label = new JLabel(labelText);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        panel.add(label, labelGbc);

        valueGbc.gridy = row;
        valueLabel.setFont(valueLabel.getFont().deriveFont(Font.PLAIN));
        panel.add(valueLabel, valueGbc);
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
