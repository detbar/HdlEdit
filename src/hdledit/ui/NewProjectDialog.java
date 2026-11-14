// src/main/java/hdledit/ui/NewProjectDialog.java
package hdledit.ui;

import hdledit.model.Project;
import hdledit.persistence.ProjectXmlSerializer;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class NewProjectDialog extends JDialog {

    private final JTextField nameField = new JTextField(30);
    private final JTextArea descriptionArea = new JTextArea(4, 30);
    private final JTextField authorField = new JTextField(30);
    private final JTextField versionField = new JTextField("1.0", 10);
    private final JTextField directoryField = new JTextField(30);
    private final JTextField libraryField = new JTextField("work", 15);

    private Project result = null;

    public NewProjectDialog(Frame owner) {
        super(owner, "Neues Projekt – HdlEdit", true);
        setLayout(new BorderLayout(10, 10));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;

        gbc.gridx = 0; gbc.gridy = row;
        form.add(new JLabel("Project Name:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        form.add(nameField, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(new JLabel("Description:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        form.add(new JScrollPane(descriptionArea), gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(new JLabel("Author:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        form.add(authorField, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(new JLabel("Version:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        form.add(versionField, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(new JLabel("Project Directory:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        JPanel dirPanel = new JPanel(new BorderLayout(5, 0));
        dirPanel.add(directoryField, BorderLayout.CENTER);
        JButton browseBtn = new JButton("Browse...");
        browseBtn.addActionListener(e -> browseDirectory());
        dirPanel.add(browseBtn, BorderLayout.EAST);
        form.add(dirPanel, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(new JLabel("Default Library:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        form.add(libraryField, gbc);

        add(form, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> {
            result = null;
            dispose();
        });
        JButton create = new JButton("Erstellen");
        create.addActionListener(e -> onCreate());
        buttons.add(cancel);
        buttons.add(create);
        add(buttons, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(owner);
        setMinimumSize(new Dimension(520, 420));
    }

    private void browseDirectory() {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle("Projektverzeichnis wählen");

        String current = directoryField.getText().trim();
        if (!current.isEmpty()) {
            File currentDir = new File(current);
            if (currentDir.exists()) {
                fc.setCurrentDirectory(currentDir);
            } else if (currentDir.getParentFile() != null && currentDir.getParentFile().exists()) {
                fc.setCurrentDirectory(currentDir.getParentFile());
            }
        }

        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            directoryField.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }

    private void onCreate() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Project Name ist Pflicht.", "Fehler", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String dirPath = directoryField.getText().trim();
        if (dirPath.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Project Directory ist Pflicht.", "Fehler", JOptionPane.ERROR_MESSAGE);
            return;
        }

        File projectDir = new File(dirPath);

        if (!projectDir.exists()) {
            if (!projectDir.mkdirs()) {
                JOptionPane.showMessageDialog(this,
                        "Das Projektverzeichnis konnte nicht erstellt werden:\n" + dirPath,
                        "Fehler", JOptionPane.ERROR_MESSAGE);
                return;
            }
        } else if (!projectDir.isDirectory()) {
            JOptionPane.showMessageDialog(this,
                    "Der angegebene Pfad ist kein Verzeichnis:\n" + dirPath,
                    "Fehler", JOptionPane.ERROR_MESSAGE);
            return;
        }

        File projectFile = new File(projectDir, name + ".hdp");

        if (projectFile.exists()) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Die Projektdatei existiert bereits:\n" + projectFile.getAbsolutePath() +
                            "\n\nMöchten Sie sie überschreiben?",
                    "Datei überschreiben?",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);

            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
        }

        result = new Project(name);
        result.setDescription(descriptionArea.getText().trim());
        result.setAuthor(authorField.getText().trim());
        result.setVersion(versionField.getText().trim());
        result.setProjectDirectory(projectDir.getAbsolutePath());
        result.setDefaultLibrary(libraryField.getText().trim());

        try {
            new ProjectXmlSerializer().save(result, projectFile);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Projektverzeichnis wurde erstellt, aber die Projektdatei konnte nicht geschrieben werden:\n"
                            + ex.getMessage(),
                    "Warnung", JOptionPane.WARNING_MESSAGE);
        }

        dispose();
    }

    public Project getResult() {
        return result;
    }
}