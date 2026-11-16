// src/main/java/hdledit/ui/BlockDiagramEditor.java
package hdledit.ui;

import hdledit.model.Block;
import hdledit.model.BlockDiagram;
import hdledit.model.Connection;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Path2D;

public class BlockDiagramEditor extends JPanel {

    private BlockDiagram diagram;
    private Block selectedBlock = null;
    private int dragOffsetX;
    private int dragOffsetY;
    private boolean dragging = false;
    private Runnable onChangeCallback;

    // Für Verbindungen
    private boolean connecting = false;
    private Block connectionSource = null;
    private int tempLineX;
    private int tempLineY;

    private final JPopupMenu contextMenu;
    private int contextX;
    private int contextY;

    private final DrawingPanel drawingPanel;

    public BlockDiagramEditor() {
        setLayout(new BorderLayout());

        JToolBar toolbar = createToolbar();
        add(toolbar, BorderLayout.NORTH);

        drawingPanel = new DrawingPanel();
        add(new JScrollPane(drawingPanel), BorderLayout.CENTER);

        // Kontextmenü
        contextMenu = new JPopupMenu();
        JMenuItem addBlockItem = new JMenuItem("Neuen Block hier einfügen");
        addBlockItem.addActionListener(e -> createBlockAt(contextX, contextY));
        contextMenu.add(addBlockItem);
    }

    private JToolBar createToolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setRollover(true);

        JButton addBlockBtn = new JButton(ToolbarIcons.ADD_BLOCK);
        addBlockBtn.setToolTipText("Neuen Block hinzufügen");
        addBlockBtn.addActionListener(e -> createBlockAt(80, 80));
        toolbar.add(addBlockBtn);

        toolbar.addSeparator();

        JLabel hint = new JLabel("  Verbindung: Alt + Ziehen von Block zu Block");
        hint.setForeground(Color.DARK_GRAY);
        toolbar.add(hint);

        return toolbar;
    }

    private void createBlockAt(int x, int y) {
        if (diagram == null) return;

        String name = JOptionPane.showInputDialog(this, "Name des Blocks:", "Neuer Block", JOptionPane.PLAIN_MESSAGE);
        if (name != null && !name.isBlank()) {
            Block block = new Block(name.trim(), x, y);
            diagram.addBlock(block);
            selectedBlock = block;
            markDirty();
            drawingPanel.repaint();
        }
    }

    public void setDiagram(BlockDiagram diagram) {
        this.diagram = diagram;
        selectedBlock = null;
        dragging = false;
        connecting = false;
        connectionSource = null;
        drawingPanel.repaint();
    }

    public void setOnChangeCallback(Runnable callback) {
        this.onChangeCallback = callback;
    }

    private void markDirty() {
        if (diagram != null) {
            diagram.setDirty(true);
            if (onChangeCallback != null) {
                onChangeCallback.run();
            }
        }
    }

    // =====================================================
    // Zeichenfläche
    // =====================================================
    private class DrawingPanel extends JPanel {

        public DrawingPanel() {
            setBackground(Color.WHITE);
            setPreferredSize(new Dimension(2000, 1500));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (e.isPopupTrigger()) {
                        showContextMenu(e);
                        return;
                    }

                    if (diagram == null) return;

                    // Alt-Taste gedrückt → Verbindungsmodus
                    if (e.isAltDown()) {
                        Block hit = findBlockAt(e.getX(), e.getY());
                        if (hit != null) {
                            connecting = true;
                            connectionSource = hit;
                            tempLineX = e.getX();
                            tempLineY = e.getY();
                            selectedBlock = hit;
                            repaint();
                            return;
                        }
                    }

                    handleMousePressed(e);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (e.isPopupTrigger()) {
                        showContextMenu(e);
                        return;
                    }

                    if (connecting && connectionSource != null) {
                        Block target = findBlockAt(e.getX(), e.getY());
                        if (target != null && target != connectionSource) {
                            // Prüfen ob Verbindung schon existiert
                            boolean exists = diagram.getConnections().stream()
                                    .anyMatch(c -> c.getFrom() == connectionSource && c.getTo() == target);

                            if (!exists) {
                                diagram.addConnection(new Connection(connectionSource, target));
                                markDirty();
                            }
                        }
                        connecting = false;
                        connectionSource = null;
                        repaint();
                        return;
                    }

                    if (dragging) {
                        dragging = false;
                        markDirty();
                    }
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e) && !e.isAltDown()) {
                        handleDoubleClick(e);
                    }
                }
            });

            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseDragged(MouseEvent e) {
                    if (connecting) {
                        tempLineX = e.getX();
                        tempLineY = e.getY();
                        repaint();
                        return;
                    }
                    handleMouseDragged(e);
                }
            });
        }

        private Block findBlockAt(int x, int y) {
            if (diagram == null) return null;
            for (int i = diagram.getBlocks().size() - 1; i >= 0; i--) {
                Block block = diagram.getBlocks().get(i);
                if (block.contains(x, y)) {
                    return block;
                }
            }
            return null;
        }

        private void showContextMenu(MouseEvent e) {
            contextX = e.getX();
            contextY = e.getY();
            contextMenu.show(this, e.getX(), e.getY());
        }

        private void handleMousePressed(MouseEvent e) {
            selectedBlock = findBlockAt(e.getX(), e.getY());
            if (selectedBlock != null) {
                dragOffsetX = e.getX() - selectedBlock.getX();
                dragOffsetY = e.getY() - selectedBlock.getY();
            }
            repaint();
        }

        private void handleMouseDragged(MouseEvent e) {
            if (selectedBlock != null) {
                dragging = true;
                selectedBlock.setX(e.getX() - dragOffsetX);
                selectedBlock.setY(e.getY() - dragOffsetY);
                repaint();
            }
        }

        private void handleDoubleClick(MouseEvent e) {
            if (selectedBlock == null) {
                createBlockAt(e.getX(), e.getY());
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (diagram == null) {
                g2.setColor(Color.GRAY);
                g2.drawString("Kein Blockdiagramm geladen", 40, 40);
                return;
            }

            // === Verbindungen zeichnen ===
            g2.setStroke(new BasicStroke(2f));
            for (Connection conn : diagram.getConnections()) {
                drawConnection(g2, conn.getFrom(), conn.getTo(), false);
            }

            // Temporäre Verbindungslinie (während Alt-Ziehen)
            if (connecting && connectionSource != null) {
                g2.setColor(new Color(30, 120, 200, 180));
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0,
                        new float[]{8, 6}, 0));
                int x1 = connectionSource.getX() + connectionSource.getWidth() / 2;
                int y1 = connectionSource.getY() + connectionSource.getHeight() / 2;
                g2.drawLine(x1, y1, tempLineX, tempLineY);
            }

            // === Blöcke zeichnen ===
            for (Block block : diagram.getBlocks()) {
                boolean isSelected = block == selectedBlock;

                // Schatten
                g2.setColor(new Color(0, 0, 0, 30));
                g2.fillRoundRect(block.getX() + 3, block.getY() + 3, block.getWidth(), block.getHeight(), 10, 10);

                // Körper
                g2.setColor(isSelected ? new Color(180, 210, 255) : new Color(230, 240, 255));
                g2.fillRoundRect(block.getX(), block.getY(), block.getWidth(), block.getHeight(), 10, 10);

                // Rahmen
                g2.setColor(isSelected ? new Color(30, 90, 180) : new Color(80, 120, 180));
                g2.setStroke(new BasicStroke(isSelected ? 2.5f : 1.5f));
                g2.drawRoundRect(block.getX(), block.getY(), block.getWidth(), block.getHeight(), 10, 10);

                // Name
                g2.setColor(Color.BLACK);
                FontMetrics fm = g2.getFontMetrics();
                int textX = block.getX() + (block.getWidth() - fm.stringWidth(block.getName())) / 2;
                int textY = block.getY() + (block.getHeight() + fm.getAscent()) / 2 - 2;
                g2.drawString(block.getName(), textX, textY);
            }
        }

        private void drawConnection(Graphics2D g2, Block from, Block to, boolean temporary) {
            int x1 = from.getX() + from.getWidth() / 2;
            int y1 = from.getY() + from.getHeight() / 2;
            int x2 = to.getX() + to.getWidth() / 2;
            int y2 = to.getY() + to.getHeight() / 2;

            g2.setColor(new Color(60, 60, 60));
            g2.setStroke(new BasicStroke(2.2f));
            g2.drawLine(x1, y1, x2, y2);

            // Pfeilspitze
            drawArrowHead(g2, x1, y1, x2, y2);
        }

        private void drawArrowHead(Graphics2D g2, int x1, int y1, int x2, int y2) {
            double angle = Math.atan2(y2 - y1, x2 - x1);
            int arrowSize = 12;

            Path2D arrow = new Path2D.Double();
            arrow.moveTo(x2, y2);
            arrow.lineTo(x2 - arrowSize * Math.cos(angle - Math.PI / 6),
                    y2 - arrowSize * Math.sin(angle - Math.PI / 6));
            arrow.lineTo(x2 - arrowSize * Math.cos(angle + Math.PI / 6),
                    y2 - arrowSize * Math.sin(angle + Math.PI / 6));
            arrow.closePath();

            g2.fill(arrow);
        }
    }
}