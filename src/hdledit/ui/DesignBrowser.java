// src/main/java/hdledit/ui/DesignBrowser.java
package hdledit.ui;

import hdledit.model.Diagram;
import hdledit.model.Project;

import javax.swing.*;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

public class DesignBrowser extends JTree {

    private Project project;
    private final DefaultMutableTreeNode rootNode;
    private final DefaultTreeModel treeModel;

    public DesignBrowser() {
        rootNode = new DefaultMutableTreeNode("Kein Projekt geladen");
        treeModel = new DefaultTreeModel(rootNode);
        setModel(treeModel);
        getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        setRootVisible(true);
        setShowsRootHandles(true);
    }

    public void setProject(Project project) {
        this.project = project;
        refresh();
    }

    public void refresh() {
        // Aktuell ausgewähltes Diagramm merken
        Diagram previouslySelected = getSelectedDiagram();

        // Listener temporär entfernen, damit beim Neuaufbau nichts feuert
        TreeSelectionListener[] listeners = getTreeSelectionListeners();
        for (TreeSelectionListener l : listeners) {
            removeTreeSelectionListener(l);
        }

        rootNode.removeAllChildren();

        DefaultMutableTreeNode nodeToSelect = null;

        if (project == null) {
            rootNode.setUserObject("Kein Projekt geladen");
        } else {
            rootNode.setUserObject(project.getName());

            for (Diagram diagram : project.getDiagrams()) {
                DefaultMutableTreeNode node = new DefaultMutableTreeNode(diagram);
                rootNode.add(node);

                if (previouslySelected != null && previouslySelected.getId().equals(diagram.getId())) {
                    nodeToSelect = node;
                }
            }
        }

        treeModel.reload();
        expandRow(0);

        // Auswahl wiederherstellen
        if (nodeToSelect != null) {
            TreePath path = new TreePath(nodeToSelect.getPath());
            setSelectionPath(path);
        }

        // Listener wieder anhängen
        for (TreeSelectionListener l : listeners) {
            addTreeSelectionListener(l);
        }
    }

    public Project getProject() {
        return project;
    }

    public Diagram getSelectedDiagram() {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) getLastSelectedPathComponent();
        if (node != null && node.getUserObject() instanceof Diagram) {
            return (Diagram) node.getUserObject();
        }
        return null;
    }
}