package com.legacybridge.client.ui;

import com.legacybridge.client.api.ApiClient;
import com.legacybridge.client.api.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;

/**
 * Dialog for uploading a document to the REST API.
 * Provides a file chooser, name field, progress bar, and Upload/Cancel buttons.
 */
public class UploadDialog extends JDialog {

    private static final Logger logger = LoggerFactory.getLogger(UploadDialog.class);

    private final ApiClient apiClient;
    private final JTextField filePathField;
    private final JTextField nameField;
    private final JProgressBar progressBar;
    private final JButton browseButton;
    private final JButton uploadButton;
    private final JButton cancelButton;

    private File selectedFile;
    private boolean uploaded = false;

    /**
     * Creates an upload dialog.
     *
     * @param owner     the parent frame
     * @param apiClient the API client to use for uploading
     */
    public UploadDialog(Frame owner, ApiClient apiClient) {
        super(owner, "Upload Document", true);
        this.apiClient = apiClient;

        setSize(500, 240);
        setLocationRelativeTo(owner);
        setResizable(false);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel contentPanel = new JPanel(new GridBagLayout());
        contentPanel.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        // File selection row
        gbc.gridx = 0;
        gbc.gridy = 0;
        contentPanel.add(new JLabel("File:"), gbc);

        filePathField = new JTextField(25);
        filePathField.setEditable(false);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        contentPanel.add(filePathField, gbc);

        browseButton = new JButton("Browse...");
        browseButton.addActionListener(e -> browseForFile());
        gbc.gridx = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        contentPanel.add(browseButton, gbc);

        // Name row
        gbc.gridx = 0;
        gbc.gridy = 1;
        contentPanel.add(new JLabel("Name:"), gbc);

        nameField = new JTextField(25);
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        contentPanel.add(nameField, gbc);
        gbc.gridwidth = 1;

        // Progress bar
        progressBar = new JProgressBar();
        progressBar.setIndeterminate(false);
        progressBar.setStringPainted(true);
        progressBar.setString("");
        progressBar.setVisible(false);
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        contentPanel.add(progressBar, gbc);
        gbc.gridwidth = 1;

        // Button row
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));

        uploadButton = new JButton("Upload");
        uploadButton.setEnabled(false);
        uploadButton.addActionListener(e -> performUpload());

        cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());

        buttonPanel.add(uploadButton);
        buttonPanel.add(cancelButton);

        setLayout(new BorderLayout());
        add(contentPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        // Enter key triggers upload
        getRootPane().setDefaultButton(uploadButton);
    }

    /**
     * Returns whether a document was successfully uploaded.
     *
     * @return true if upload succeeded
     */
    public boolean isUploaded() {
        return uploaded;
    }

    private void browseForFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select Document to Upload");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setMultiSelectionEnabled(false);

        fileChooser.addChoosableFileFilter(new FileNameExtensionFilter(
                "Documents (PDF, DOC, DOCX, TXT)", "pdf", "doc", "docx", "txt"));
        fileChooser.addChoosableFileFilter(new FileNameExtensionFilter(
                "Images (PNG, JPG, GIF)", "png", "jpg", "jpeg", "gif"));
        fileChooser.setAcceptAllFileFilterUsed(true);

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            selectedFile = fileChooser.getSelectedFile();
            filePathField.setText(selectedFile.getAbsolutePath());
            nameField.setText(selectedFile.getName());
            uploadButton.setEnabled(true);
        }
    }

    private void performUpload() {
        if (selectedFile == null || !selectedFile.exists()) {
            JOptionPane.showMessageDialog(this,
                    "Please select a valid file to upload.",
                    "No File Selected",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Disable controls during upload
        browseButton.setEnabled(false);
        nameField.setEnabled(false);
        uploadButton.setEnabled(false);
        cancelButton.setEnabled(false);

        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);
        progressBar.setString("Uploading...");

        SwingWorker<Document, Void> worker = new SwingWorker<Document, Void>() {
            @Override
            protected Document doInBackground() {
                return apiClient.uploadDocument(selectedFile);
            }

            @Override
            protected void done() {
                progressBar.setIndeterminate(false);
                try {
                    Document result = get();
                    if (result != null) {
                        progressBar.setValue(100);
                        progressBar.setString("Upload complete!");
                        uploaded = true;
                        logger.info("Document uploaded successfully: {}", result.getName());

                        JOptionPane.showMessageDialog(UploadDialog.this,
                                "Document \"" + result.getName() + "\" uploaded successfully.",
                                "Upload Complete",
                                JOptionPane.INFORMATION_MESSAGE);
                        dispose();
                    } else {
                        progressBar.setString("Upload failed");
                        JOptionPane.showMessageDialog(UploadDialog.this,
                                "Upload failed. The server did not return a valid response.\nPlease check the server logs.",
                                "Upload Failed",
                                JOptionPane.ERROR_MESSAGE);
                        resetControls();
                    }
                } catch (Exception ex) {
                    progressBar.setString("Upload failed");
                    logger.error("Upload failed", ex);
                    JOptionPane.showMessageDialog(UploadDialog.this,
                            "Upload failed:\n" + ex.getMessage(),
                            "Upload Error",
                            JOptionPane.ERROR_MESSAGE);
                    resetControls();
                }
            }
        };
        worker.execute();
    }

    private void resetControls() {
        browseButton.setEnabled(true);
        nameField.setEnabled(true);
        uploadButton.setEnabled(selectedFile != null);
        cancelButton.setEnabled(true);
    }
}
