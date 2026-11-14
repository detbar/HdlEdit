// src/main/java/hdledit/ui/AboutDialog.java
package hdledit.ui;

import javax.swing.*;
import java.awt.*;

public class AboutDialog extends JDialog {

    public AboutDialog(Frame owner) {
        super(owner, "About HdlEdit", true);
        setLayout(new BorderLayout(15, 15));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));

        // Logo laden
        JLabel logoLabel = new JLabel();
        try {
            ImageIcon icon = new ImageIcon(getClass().getResource("/hdledit/resources/HdlEditLogo.png"));
            // Optional etwas skalieren, falls das Logo zu groß ist
            Image scaled = icon.getImage().getScaledInstance(180, 100, Image.SCALE_SMOOTH);
            logoLabel.setIcon(new ImageIcon(scaled));
        } catch (Exception e) {
            logoLabel.setText("[Logo nicht gefunden]");
        }
        logoLabel.setHorizontalAlignment(SwingConstants.CENTER);

        // Text
        JLabel title = new JLabel("HdlEdit", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));

        JLabel version = new JLabel("Version " + MainWindow.APP_VERSION, SwingConstants.CENTER);
        version.setFont(version.getFont().deriveFont(14f));

        JLabel description = new JLabel(
                "<html><center>Vereinfachter grafischer HDL-Editor<br>" +
                        "für Blockdiagramme, Tabellen und Flussdiagramme</center></html>",
                SwingConstants.CENTER);

        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.add(title);
        textPanel.add(Box.createVerticalStrut(6));
        textPanel.add(version);
        textPanel.add(Box.createVerticalStrut(12));
        textPanel.add(description);

        // Zusammenbauen
        JPanel center = new JPanel(new BorderLayout(10, 15));
        center.add(logoLabel, BorderLayout.NORTH);
        center.add(textPanel, BorderLayout.CENTER);

        add(center, BorderLayout.CENTER);

        // Schließen-Button
        JButton close = new JButton("Schließen");
        close.addActionListener(e -> dispose());
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(close);
        add(buttonPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(owner);
        setResizable(false);
    }
}