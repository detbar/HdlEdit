// src/main/java/hdledit/ui/MainWindow.java
package hdledit.ui;

import hdledit.model.*;
import hdledit.persistence.ProjectXmlSerializer;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import java.awt.*;
import java.io.File;
import java.util.prefs.Preferences;

public class MainWindow extends JFrame {

    public static final String APP_NAME = "HdlEdit";
    public static final String APP_VERSION = "0.0.4";

    private static final String PREF_LAST_PROJECT = "lastProjectPath";

    private final DesignBrowser designBrowser;
    private final JTextArea consoleArea;
    private final BlockDiagramEditor blockDiagramEditor;
    private final JPanel editorContainer;
    private final JLabel editorPlaceholder;

    private Project currentProject;
    private File currentProjectFile;
    private Diagram currentlyShownDiagram = null;

    private final ProjectXmlSerializer serializer = new ProjectXmlSerializer();
    private final Preferences prefs = Preferences.userNodeForPackage(MainWindow.class);

    // Menüeinträge
    private JMenuItem saveProjectItem;
    private JMenuItem closeProjectItem;
    private JMenuItem addBlockItem;
    private JMenuItem addTableItem;
    private JMenuItem addFlowItem;
    private JButton toolbarSaveButton;
    private JButton toolbarCloseButton;
    private JButton toolbarAddBlockButton;

    public MainWindow() {
        setTitle(APP_NAME + " " + APP_VERSION);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1280, 840);
        setLocationRelativeTo(null);

        designBrowser = new DesignBrowser();
        blockDiagramEditor = new BlockDiagramEditor();

        // Dirty-Callback: nur den Tree aktualisieren (Stern)
        blockDiagramEditor.setOnChangeCallback(() -> designBrowser.refresh());

        editorPlaceholder = new JLabel("Editor-Bereich (noch leer)", SwingConstants.CENTER);
        editorPlaceholder.setFont(editorPlaceholder.getFont().deriveFont(16f));
        editorPlaceholder.setForeground(Color.GRAY);

        editorContainer = new JPanel(new BorderLayout());
        editorContainer.add(editorPlaceholder, BorderLayout.CENTER);

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
                editorContainer
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
        add(createToolbar(), BorderLayout.NORTH);
        add(verticalSplit, BorderLayout.CENTER);

        // Selection Listener
        designBrowser.addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent e) {
                onDiagramSelected();
            }
        });

        setupMacOSAbout();
        updateMenuState();

        log(APP_NAME + " " + APP_VERSION + " gestartet.");
        loadLastProject();
    }

    private JToolBar createToolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setRollover(true);

        JButton newBtn = new JButton(ToolbarIcons.NEW_PROJECT);
        newBtn.setToolTipText("Neues Projekt");
        newBtn.addActionListener(e -> createNewProject());
        toolbar.add(newBtn);

        JButton openBtn = new JButton(ToolbarIcons.OPEN_PROJECT);
        openBtn.setToolTipText("Projekt öffnen");
        openBtn.addActionListener(e -> openProject());
        toolbar.add(openBtn);

        JButton saveBtn = new JButton(ToolbarIcons.SAVE_PROJECT);
        saveBtn.setToolTipText("Projekt speichern");
        saveBtn.addActionListener(e -> saveProject());
        toolbar.add(saveBtn);

        JButton closeBtn = new JButton(ToolbarIcons.CLOSE_PROJECT);
        closeBtn.setToolTipText("Projekt schließen");
        closeBtn.addActionListener(e -> closeProject());
        toolbar.add(closeBtn);

        toolbar.addSeparator();

        JButton addBlockBtn = new JButton(ToolbarIcons.ADD_BLOCK);
        addBlockBtn.setToolTipText("Blockdiagramm hinzufügen");
        addBlockBtn.addActionListener(e -> addDiagram(DiagramType.BLOCK_DIAGRAM));
        toolbar.add(addBlockBtn);

        // Referenz speichern, damit wir sie enablen/disablen können
        this.toolbarSaveButton = saveBtn;
        this.toolbarCloseButton = closeBtn;
        this.toolbarAddBlockButton = addBlockBtn;

        return toolbar;
    }

    private void onDiagramSelected() {
        Diagram selected = designBrowser.getSelectedDiagram();

        // Über ID vergleichen (robuster als Referenz-Vergleich)
        String selectedId = (selected != null) ? selected.getId() : null;
        String currentId  = (currentlyShownDiagram != null) ? currentlyShownDiagram.getId() : null;

        if (java.util.Objects.equals(selectedId, currentId)) {
            return; // gleiches Diagramm → nichts tun
        }

        currentlyShownDiagram = selected;

        editorContainer.removeAll();

        if (selected instanceof BlockDiagram blockDiagram) {
            blockDiagramEditor.setDiagram(blockDiagram);
            editorContainer.add(blockDiagramEditor, BorderLayout.CENTER);
            log("Blockdiagramm geöffnet: " + blockDiagram.getName());
        } else {
            editorContainer.add(editorPlaceholder, BorderLayout.CENTER);
        }

        editorContainer.revalidate();
        editorContainer.repaint();
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

        if (toolbarSaveButton != null) {
            toolbarSaveButton.setEnabled(hasProject);
            toolbarCloseButton.setEnabled(hasProject);
            toolbarAddBlockButton.setEnabled(hasProject);
        }
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
            currentlyShownDiagram = null;
            onDiagramSelected();
            log("Neues Projekt erstellt: " + project.getName());
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
            currentlyShownDiagram = null;
            onDiagramSelected();
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

            // Alle Diagramme als sauber markieren
            for (Diagram d : currentProject.getDiagrams()) {
                d.setDirty(false);
            }
            designBrowser.refresh();

            log("Projekt gespeichert: " + currentProjectFile.getAbsolutePath());
        } catch (Exception ex) {
            showError("Fehler beim Speichern", ex.getMessage());
            log("FEHLER beim Speichern: " + ex.getMessage());
        }
    }

    private void closeProject() {
        currentProject = null;
        currentProjectFile = null;
        currentlyShownDiagram = null;
        designBrowser.setProject(null);
        updateMenuState();
        clearLastProjectPath();
        onDiagramSelected();
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