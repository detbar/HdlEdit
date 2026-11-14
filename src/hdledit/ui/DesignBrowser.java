// src/main/java/hdledit/ui/DesignBrowser.java
package hdledit.ui;

import hdledit.model.Diagram;
import hdledit.model.Project;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
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
        rootNode.removeAllChildren();

        if (project == null) {
            rootNode.setUserObject("Kein Projekt geladen");
        } else {
            rootNode.setUserObject(project.getName());

            for (Diagram diagram : project.getDiagrams()) {
                String prefix = switch (diagram.getType()) {
                    case BLOCK_DIAGRAM -> "[Block] ";
                    case TABLE        -> "[Tabelle] ";
                    case FLOWCHART    -> "[Fluss] ";
                };
                DefaultMutableTreeNode node = new DefaultMutableTreeNode(prefix + diagram.getName());
                node.setUserObject(diagram);
                rootNode.add(node);
            }
        }

        treeModel.reload();
        expandRow(0);
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