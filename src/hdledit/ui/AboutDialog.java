// src/main/java/hdledit/ui/AboutDialog.java
package hdledit.ui;

import hdledit.util.GitInfo;

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
            Image scaled = icon.getImage().getScaledInstance(180, 100, Image.SCALE_SMOOTH);
            logoLabel.setIcon(new ImageIcon(scaled));
        } catch (Exception e) {
            logoLabel.setText("[Logo nicht gefunden]");
        }
        logoLabel.setHorizontalAlignment(SwingConstants.CENTER);

        // Text
        JLabel title = new JLabel("HdlEdit", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));

        String versionText = "Version " + MainWindow.APP_VERSION + " (GIT: " + GitInfo.getVersionSuffix() + ")";
        JLabel version = new JLabel(versionText, SwingConstants.CENTER);
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

        JPanel center = new JPanel(new BorderLayout(10, 15));
        center.add(logoLabel, BorderLayout.NORTH);
        center.add(textPanel, BorderLayout.CENTER);

        add(center, BorderLayout.CENTER);

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