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
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Path2D;

public class BlockDiagramEditor extends JPanel {

    private BlockDiagram diagram;
    private Block selectedBlock = null;
    private int dragOffsetX;
    private int dragOffsetY;
    private boolean dragging = false;
    private Runnable onChangeCallback;

    // Verbindungsmodus
    private boolean connectMode = false;
    private boolean connecting = false;
    private Block connectionSource = null;
    private int tempLineX;
    private int tempLineY;
    private Connection.Style currentStyle = Connection.Style.DIRECT;

    private final JToggleButton connectToggle;
    private final JComboBox<Connection.Style> styleCombo;
    private final JPopupMenu contextMenu;
    private int contextX;
    private int contextY;

    private final DrawingPanel drawingPanel;

    public BlockDiagramEditor() {
        setLayout(new BorderLayout());

        // Zeichenfläche zuerst (wegen definite assignment)
        drawingPanel = new DrawingPanel();

        // Toolbar
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setRollover(true);

        JButton addBlockBtn = new JButton(ToolbarIcons.ADD_BLOCK);
        addBlockBtn.setToolTipText("Neuen Block hinzufügen");
        addBlockBtn.addActionListener(e -> createBlockAt(80, 80));
        toolbar.add(addBlockBtn);

        toolbar.addSeparator();

        connectToggle = new JToggleButton(ToolbarIcons.CONNECT);
        connectToggle.setToolTipText("Verbindungsmodus ein/aus");
        connectToggle.addActionListener(e -> {
            connectMode = connectToggle.isSelected();
            connecting = false;
            connectionSource = null;
            drawingPanel.repaint();
        });
        toolbar.add(connectToggle);

        toolbar.addSeparator();

        toolbar.add(new JLabel(" Stil: "));
        styleCombo = new JComboBox<>(Connection.Style.values());
        styleCombo.setSelectedItem(Connection.Style.DIRECT);
        styleCombo.setMaximumSize(new Dimension(130, 28));
        styleCombo.setToolTipText("Linienart der neuen Verbindungen");
        styleCombo.addActionListener(e -> {
            currentStyle = (Connection.Style) styleCombo.getSelectedItem();
        });
        toolbar.add(styleCombo);

        add(toolbar, BorderLayout.NORTH);
        add(new JScrollPane(drawingPanel), BorderLayout.CENTER);

        // Kontextmenü
        contextMenu = new JPopupMenu();
        JMenuItem addBlockItem = new JMenuItem("Neuen Block hier einfügen");
        addBlockItem.addActionListener(e -> createBlockAt(contextX, contextY));
        contextMenu.add(addBlockItem);
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
        connectMode = false;
        connectToggle.setSelected(false);
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

                    if (connectMode) {
                        Block hit = findBlockAt(e.getX(), e.getY());
                        if (hit != null) {
                            connecting = true;
                            connectionSource = hit;
                            tempLineX = e.getX();
                            tempLineY = e.getY();
                            selectedBlock = hit;
                            repaint();
                        }
                        return;
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
                            boolean exists = diagram.getConnections().stream()
                                    .anyMatch(c -> c.getFrom() == connectionSource && c.getTo() == target);
                            if (!exists) {
                                diagram.addConnection(new Connection(connectionSource, target, currentStyle));
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
                    if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e) && !connectMode) {
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
                if (block.contains(x, y)) return block;
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

            // Bestehende Verbindungen
            for (Connection conn : diagram.getConnections()) {
                drawConnection(g2, conn);
            }

            // Temporäre Linie während des Ziehens
            if (connecting && connectionSource != null) {
                g2.setColor(new Color(30, 120, 200, 180));
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0,
                        new float[]{8, 6}, 0));
                int x1 = centerX(connectionSource);
                int y1 = centerY(connectionSource);
                drawStyledLine(g2, x1, y1, tempLineX, tempLineY, currentStyle, true);
            }

            // Blöcke
            for (Block block : diagram.getBlocks()) {
                boolean isSelected = block == selectedBlock;

                g2.setColor(new Color(0, 0, 0, 30));
                g2.fillRoundRect(block.getX() + 3, block.getY() + 3, block.getWidth(), block.getHeight(), 10, 10);

                g2.setColor(isSelected ? new Color(180, 210, 255) : new Color(230, 240, 255));
                g2.fillRoundRect(block.getX(), block.getY(), block.getWidth(), block.getHeight(), 10, 10);

                g2.setColor(isSelected ? new Color(30, 90, 180) : new Color(80, 120, 180));
                g2.setStroke(new BasicStroke(isSelected ? 2.5f : 1.5f));
                g2.drawRoundRect(block.getX(), block.getY(), block.getWidth(), block.getHeight(), 10, 10);

                g2.setColor(Color.BLACK);
                FontMetrics fm = g2.getFontMetrics();
                int textX = block.getX() + (block.getWidth() - fm.stringWidth(block.getName())) / 2;
                int textY = block.getY() + (block.getHeight() + fm.getAscent()) / 2 - 2;
                g2.drawString(block.getName(), textX, textY);
            }
        }

        private int centerX(Block b) {
            return b.getX() + b.getWidth() / 2;
        }

        private int centerY(Block b) {
            return b.getY() + b.getHeight() / 2;
        }

        private void drawConnection(Graphics2D g2, Connection conn) {
            int x1 = centerX(conn.getFrom());
            int y1 = centerY(conn.getFrom());
            int x2 = centerX(conn.getTo());
            int y2 = centerY(conn.getTo());

            g2.setColor(new Color(60, 60, 60));
            g2.setStroke(new BasicStroke(2.2f));
            drawStyledLine(g2, x1, y1, x2, y2, conn.getStyle(), false);
            drawArrowHead(g2, x1, y1, x2, y2, conn.getStyle());
        }

        private void drawStyledLine(Graphics2D g2, int x1, int y1, int x2, int y2,
                                    Connection.Style style, boolean temporary) {
            switch (style) {
                case ORTHOGONAL -> drawOrthogonal(g2, x1, y1, x2, y2);
                case BEZIER     -> drawBezier(g2, x1, y1, x2, y2);
                default         -> g2.drawLine(x1, y1, x2, y2); // DIRECT
            }
        }

        private void drawOrthogonal(Graphics2D g2, int x1, int y1, int x2, int y2) {
            // Einfache rechtwinklige Verbindung (Z- oder L-Form)
            int midX = (x1 + x2) / 2;
            Path2D path = new Path2D.Double();
            path.moveTo(x1, y1);
            path.lineTo(midX, y1);
            path.lineTo(midX, y2);
            path.lineTo(x2, y2);
            g2.draw(path);
        }

        private void drawBezier(Graphics2D g2, int x1, int y1, int x2, int y2) {
            double dx = x2 - x1;
            double dy = y2 - y1;
            // Kontrollpunkte seitlich versetzt für schöne Kurve
            double cx1 = x1 + dx * 0.4;
            double cy1 = y1;
            double cx2 = x2 - dx * 0.4;
            double cy2 = y2;

            // Bei eher vertikalen Verbindungen die Kontrollpunkte anders setzen
            if (Math.abs(dy) > Math.abs(dx)) {
                cx1 = x1;
                cy1 = y1 + dy * 0.4;
                cx2 = x2;
                cy2 = y2 - dy * 0.4;
            }

            CubicCurve2D curve = new CubicCurve2D.Double(x1, y1, cx1, cy1, cx2, cy2, x2, y2);
            g2.draw(curve);
        }

        private void drawArrowHead(Graphics2D g2, int x1, int y1, int x2, int y2, Connection.Style style) {
            // Pfeil am Zielende
            double angle;
            if (style == Connection.Style.ORTHOGONAL) {
                // Letztes Segment ist horizontal oder vertikal
                int midX = (x1 + x2) / 2;
                if (Math.abs(x2 - midX) > 2) {
                    angle = (x2 > midX) ? 0 : Math.PI;
                } else {
                    angle = (y2 > y1) ? Math.PI / 2 : -Math.PI / 2;
                }
            } else {
                angle = Math.atan2(y2 - y1, x2 - x1);
            }

            int arrowSize = 11;
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