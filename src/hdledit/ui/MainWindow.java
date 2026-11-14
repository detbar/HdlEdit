// src/main/java/hdledit/ui/MainWindow.java
package hdledit.ui;

import hdledit.model.*;
import hdledit.persistence.ProjectXmlSerializer;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.prefs.Preferences;

public class MainWindow extends JFrame {

    public static final String APP_NAME = "HdlEdit";
    public static final String APP_VERSION = "0.0.0";

    private static final String PREF_LAST_PROJECT = "lastProjectPath";

    private final DesignBrowser designBrowser;
    private final JTextArea consoleArea;
    private Project currentProject;
    private File currentProjectFile;          // merkt sich die aktuelle .hdp-Datei
    private final ProjectXmlSerializer serializer = new ProjectXmlSerializer();
    private final Preferences prefs = Preferences.userNodeForPackage(MainWindow.class);

    // Menüeinträge
    private JMenuItem saveProjectItem;
    private JMenuItem closeProjectItem;
    private JMenuItem addBlockItem;
    private JMenuItem addTableItem;
    private JMenuItem addFlowItem;

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

        setupMacOSAbout();
        updateMenuState();

        log(APP_NAME + " " + APP_VERSION + " gestartet.");

        // Letztes Projekt automatisch laden
        loadLastProject();
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // === Datei ===
        JMenu fileMenu = new JMenu("Datei");

        JMenuItem newProject = new JMenuItem("Neues Projekt...");
        newProject.addActionListener(e -> createNewProject());

        JMenuItem openProject = new JMenuItem("Projekt öffnen...");
        openProject.addActionListener(e -> openProject());

        saveProjectItem = new JMenuItem("Projekt speichern...");
        saveProjectItem.addActionListener(e -> saveProject());

        closeProjectItem = new JMenuItem("Projekt schließen");
        closeProjectItem.addActionListener(e -> closeProject());

        JMenuItem exit = new JMenuItem("Beenden");
        exit.addActionListener(e -> System.exit(0));

        fileMenu.add(newProject);
        fileMenu.add(openProject);
        fileMenu.add(saveProjectItem);
        fileMenu.add(closeProjectItem);
        fileMenu.addSeparator();
        fileMenu.add(exit);

        // === Diagramm ===
        JMenu diagramMenu = new JMenu("Diagramm");

        addBlockItem = new JMenuItem("Blockdiagramm hinzufügen");
        addBlockItem.addActionListener(e -> addDiagram(DiagramType.BLOCK_DIAGRAM));

        addTableItem = new JMenuItem("Tabelle hinzufügen");
        addTableItem.addActionListener(e -> addDiagram(DiagramType.TABLE));

        addFlowItem = new JMenuItem("Flussdiagramm hinzufügen");
        addFlowItem.addActionListener(e -> addDiagram(DiagramType.FLOWCHART));

        diagramMenu.add(addBlockItem);
        diagramMenu.add(addTableItem);
        diagramMenu.add(addFlowItem);

        menuBar.add(fileMenu);
        menuBar.add(diagramMenu);

        // Hilfe nur unter Windows/Linux
        if (!System.getProperty("os.name").toLowerCase().contains("mac")) {
            JMenu helpMenu = new JMenu("Hilfe");
            JMenuItem aboutItem = new JMenuItem("About HdlEdit...");
            aboutItem.addActionListener(e -> {
                AboutDialog about = new AboutDialog(this);
                about.setVisible(true);
            });
            helpMenu.add(aboutItem);
            menuBar.add(helpMenu);
        }

        return menuBar;
    }

    private void updateMenuState() {
        boolean hasProject = currentProject != null;

        saveProjectItem.setEnabled(hasProject);
        closeProjectItem.setEnabled(hasProject);
        addBlockItem.setEnabled(hasProject);
        addTableItem.setEnabled(hasProject);
        addFlowItem.setEnabled(hasProject);
    }

    private void createNewProject() {
        NewProjectDialog dialog = new NewProjectDialog(this);
        dialog.setVisible(true);

        Project project = dialog.getResult();
        if (project != null) {
            currentProject = project;
            currentProjectFile = new File(project.getProjectDirectory(), project.getName() + ".hdp");
            designBrowser.setProject(currentProject);
            updateMenuState();
            saveLastProjectPath(currentProjectFile.getAbsolutePath());
            log("Neues Projekt erstellt: " + project.getName());
            log("Verzeichnis: " + project.getProjectDirectory());
        }
    }

    private void openProject() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("HdlEdit Projekt (*.hdp)", "hdp"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            openProjectFile(fc.getSelectedFile());
        }
    }

    private void openProjectFile(File file) {
        try {
            currentProject = serializer.load(file);
            currentProjectFile = file;
            designBrowser.setProject(currentProject);
            updateMenuState();
            saveLastProjectPath(file.getAbsolutePath());
            log("Projekt geladen: " + currentProject.getName());
        } catch (Exception ex) {
            showError("Fehler beim Laden", ex.getMessage());
            log("FEHLER beim Laden: " + ex.getMessage());
        }
    }

    private void saveProject() {
        if (currentProject == null || currentProjectFile == null) return;

        try {
            serializer.save(currentProject, currentProjectFile);
            log("Projekt gespeichert: " + currentProjectFile.getAbsolutePath());
        } catch (Exception ex) {
            showError("Fehler beim Speichern", ex.getMessage());
            log("FEHLER beim Speichern: " + ex.getMessage());
        }
    }

    private void closeProject() {
        currentProject = null;
        currentProjectFile = null;
        designBrowser.setProject(null);
        updateMenuState();
        clearLastProjectPath();
        log("Projekt geschlossen.");
    }

    private void loadLastProject() {
        String path = prefs.get(PREF_LAST_PROJECT, null);
        if (path != null) {
            File file = new File(path);
            if (file.exists()) {
                openProjectFile(file);
            } else {
                clearLastProjectPath();
            }
        }
    }

    private void saveLastProjectPath(String path) {
        prefs.put(PREF_LAST_PROJECT, path);
    }

    private void clearLastProjectPath() {
        prefs.remove(PREF_LAST_PROJECT);
    }

    private void addDiagram(DiagramType type) {
        if (currentProject == null) return;

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

    public void log(String message) {
        consoleArea.append("> " + message + "\n");
        consoleArea.setCaretPosition(consoleArea.getDocument().getLength());
    }

    private void showError(String title, String message) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) {
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