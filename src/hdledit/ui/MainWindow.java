// src/main/java/hdledit/ui/MainWindow.java
package hdledit.ui;

import hdledit.model.*;
import hdledit.persistence.ProjectXmlSerializer;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class MainWindow extends JFrame {

    public static final String APP_NAME = "HdlEdit";
    public static final String APP_VERSION = "0.0.0";

    private final DesignBrowser designBrowser;
    private final JTextArea consoleArea;
    private Project currentProject;
    private final ProjectXmlSerializer serializer = new ProjectXmlSerializer();

    public MainWindow() {
        setTitle(APP_NAME + " " + APP_VERSION);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1280, 840);
        setLocationRelativeTo(null);

        designBrowser = new DesignBrowser();

        JLabel editorPlaceholder = new JLabel("Editor-Bereich (noch leer)", SwingConstants.CENTER);
        editorPlaceholder.setFont(editorPlaceholder.getFont().deriveFont(16f));
        editorPlaceholder.setForeground(Color.GRAY);

        consoleArea = new JTextArea();
        consoleArea.setEditable(false);
        consoleArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        consoleArea.setBackground(new Color(30, 30, 30));
        consoleArea.setForeground(new Color(200, 200, 200));
        consoleArea.setMargin(new Insets(6, 8, 6, 8));
        JScrollPane consoleScroll = new JScrollPane(consoleArea);
        consoleScroll.setBorder(BorderFactory.createTitledBorder("Konsole"));

        JSplitPane horizontalSplit = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(designBrowser),
                editorPlaceholder
        );
        horizontalSplit.setDividerLocation(280);
        horizontalSplit.setResizeWeight(0.22);
        horizontalSplit.setOneTouchExpandable(true);

        JSplitPane verticalSplit = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                horizontalSplit,
                consoleScroll
        );
        verticalSplit.setDividerLocation(620);
        verticalSplit.setResizeWeight(0.78);
        verticalSplit.setOneTouchExpandable(true);

        setJMenuBar(createMenuBar());
        add(verticalSplit, BorderLayout.CENTER);

        log("HdlEdit gestartet.");
        setupMacOSAbout();
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("Datei");

        JMenuItem newProject = new JMenuItem("Neues Projekt...");
        newProject.addActionListener(e -> createNewProject());

        JMenuItem openProject = new JMenuItem("Projekt öffnen...");
        openProject.addActionListener(e -> openProject());

        JMenuItem saveProject = new JMenuItem("Projekt speichern...");
        saveProject.addActionListener(e -> saveProject());

        JMenuItem exit = new JMenuItem("Beenden");
        exit.addActionListener(e -> System.exit(0));

        fileMenu.add(newProject);
        fileMenu.add(openProject);
        fileMenu.add(saveProject);
        fileMenu.addSeparator();
        fileMenu.add(exit);

        JMenu diagramMenu = new JMenu("Diagramm");

        JMenuItem addBlock = new JMenuItem("Blockdiagramm hinzufügen");
        addBlock.addActionListener(e -> addDiagram(DiagramType.BLOCK_DIAGRAM));

        JMenuItem addTable = new JMenuItem("Tabelle hinzufügen");
        addTable.addActionListener(e -> addDiagram(DiagramType.TABLE));

        JMenuItem addFlow = new JMenuItem("Flussdiagramm hinzufügen");
        addFlow.addActionListener(e -> addDiagram(DiagramType.FLOWCHART));

        diagramMenu.add(addBlock);
        diagramMenu.add(addTable);
        diagramMenu.add(addFlow);

        menuBar.add(fileMenu);
        menuBar.add(diagramMenu);
        JMenu helpMenu = new JMenu("Hilfe");
        JMenuItem aboutItem = new JMenuItem("About HdlEdit...");
        aboutItem.addActionListener(e -> {
            AboutDialog about = new AboutDialog(this);
            about.setVisible(true);
        });
        if (!System.getProperty("os.name").toLowerCase().contains("mac")) {
            helpMenu = new JMenu("Hilfe");
            aboutItem = new JMenuItem("About HdlEdit...");
            aboutItem.addActionListener(e -> {
                AboutDialog about = new AboutDialog(this);
                about.setVisible(true);
            });
            helpMenu.add(aboutItem);
            menuBar.add(helpMenu);
        }        return menuBar;
    }

    private void createNewProject() {
        NewProjectDialog dialog = new NewProjectDialog(this);
        dialog.setVisible(true);

        Project project = dialog.getResult();
        if (project != null) {
            currentProject = project;
            designBrowser.setProject(currentProject);
            log("Neues Projekt erstellt: " + project.getName());
            log("Verzeichnis: " + project.getProjectDirectory());
        }
    }

    private void openProject() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("HdlEdit Projekt (*.hdp)", "hdp"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                currentProject = serializer.load(fc.getSelectedFile());
                designBrowser.setProject(currentProject);
                log("Projekt geladen: " + currentProject.getName());
            } catch (Exception ex) {
                showError("Fehler beim Laden", ex.getMessage());
                log("FEHLER beim Laden: " + ex.getMessage());
            }
        }
    }

    private void saveProject() {
        if (currentProject == null) {
            showError("Kein Projekt", "Es ist kein Projekt geladen.");
            return;
        }

        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("HdlEdit Projekt (*.hdp)", "hdp"));
        fc.setSelectedFile(new File(currentProject.getName() + ".hdp"));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                serializer.save(currentProject, fc.getSelectedFile());
                log("Projekt gespeichert: " + fc.getSelectedFile().getAbsolutePath());
            } catch (Exception ex) {
                showError("Fehler beim Speichern", ex.getMessage());
                log("FEHLER beim Speichern: " + ex.getMessage());
            }
        }
    }

    private void addDiagram(DiagramType type) {
        if (currentProject == null) {
            showError("Kein Projekt", "Bitte zuerst ein Projekt anlegen oder öffnen.");
            return;
        }

        String name = JOptionPane.showInputDialog(this, "Name des Diagramms:", "Diagramm hinzufügen", JOptionPane.PLAIN_MESSAGE);
        if (name != null && !name.isBlank()) {
            Diagram diagram = switch (type) {
                case BLOCK_DIAGRAM -> new BlockDiagram(name.trim());
                case TABLE         -> new TableDiagram(name.trim());
                case FLOWCHART     -> new Flowchart(name.trim());
            };
            currentProject.addDiagram(diagram);
            designBrowser.refresh();
            log("Diagramm hinzugefügt: " + name + " (" + type + ")");
        }
    }

    public void log(String message) {
        consoleArea.append("> " + message + "\n");
        consoleArea.setCaretPosition(consoleArea.getDocument().getLength());
    }

    private void showError(String title, String message) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }

    private void setupMacOSAbout() {
        if (!System.getProperty("os.name").toLowerCase().contains("mac")) {
            return;
        }

        if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.APP_ABOUT)) {
                desktop.setAboutHandler(e -> {
                    AboutDialog about = new AboutDialog(this);
                    about.setVisible(true);
                });
            }
        }
    }

    public static void main(String[] args) {
        // macOS: Name neben dem Apfel
        System.setProperty("apple.awt.application.name", APP_NAME);

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            MainWindow window = new MainWindow();
            window.setVisible(true);
        });
    }
}