package com.legacybridge.client.ui;

import javax.swing.*;
import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Status bar panel displayed at the bottom of the main frame.
 * Shows connection status, document count, and last refresh time.
 */
public class StatusBar extends JPanel {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final StatusIndicator statusIndicator;
    private final JLabel connectionLabel;
    private final JLabel documentCountLabel;
    private final JLabel lastRefreshLabel;

    public StatusBar() {
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, UIManager.getColor("Separator.foreground")),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));

        statusIndicator = new StatusIndicator();
        connectionLabel = new JLabel("Disconnected");
        connectionLabel.setFont(connectionLabel.getFont().deriveFont(Font.PLAIN, 12f));

        documentCountLabel = new JLabel("0 documents");
        documentCountLabel.setFont(documentCountLabel.getFont().deriveFont(Font.PLAIN, 12f));

        lastRefreshLabel = new JLabel("Never refreshed");
        lastRefreshLabel.setFont(lastRefreshLabel.getFont().deriveFont(Font.PLAIN, 12f));

        add(statusIndicator);
        add(Box.createRigidArea(new Dimension(6, 0)));
        add(connectionLabel);
        add(Box.createRigidArea(new Dimension(20, 0)));
        add(new JSeparator(SwingConstants.VERTICAL) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(2, 20);
            }
        });
        add(Box.createRigidArea(new Dimension(20, 0)));
        add(documentCountLabel);
        add(Box.createHorizontalGlue());
        add(lastRefreshLabel);
    }

    /**
     * Updates the connection status display.
     *
     * @param connected true if connected, false otherwise
     */
    public void setConnected(boolean connected) {
        statusIndicator.setConnected(connected);
        connectionLabel.setText(connected ? "Connected to REST API" : "Disconnected");
    }

    /**
     * Updates the document count display.
     *
     * @param count the number of documents
     */
    public void setDocumentCount(int count) {
        documentCountLabel.setText(count + " document" + (count != 1 ? "s" : ""));
    }

    /**
     * Updates the last refresh time to the current time.
     */
    public void updateRefreshTime() {
        String time = LocalTime.now().format(TIME_FORMATTER);
        lastRefreshLabel.setText("Last refresh: " + time);
    }

    /**
     * Small painted circle that indicates connection status (green or red).
     */
    private static class StatusIndicator extends JComponent {

        private boolean connected = false;

        StatusIndicator() {
            setPreferredSize(new Dimension(14, 14));
            setMaximumSize(new Dimension(14, 14));
            setMinimumSize(new Dimension(14, 14));
        }

        void setConnected(boolean connected) {
            this.connected = connected;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int diameter = 10;
            int x = (getWidth() - diameter) / 2;
            int y = (getHeight() - diameter) / 2;

            if (connected) {
                g2.setColor(new Color(76, 175, 80));
            } else {
                g2.setColor(new Color(244, 67, 54));
            }
            g2.fillOval(x, y, diameter, diameter);

            g2.setColor(g2.getColor().darker());
            g2.drawOval(x, y, diameter, diameter);

            g2.dispose();
        }
    }
}
