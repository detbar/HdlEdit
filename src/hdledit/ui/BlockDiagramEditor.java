// src/main/java/hdledit/ui/BlockDiagramEditor.java
package hdledit.ui;

import hdledit.model.Block;
import hdledit.model.BlockDiagram;
import hdledit.model.Connection;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Path2D;

public class BlockDiagramEditor extends JPanel {

    private BlockDiagram diagram;
    private Block selectedBlock = null;
    private Connection selectedConnection = null;
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

    // Endpunkt einer bestehenden Verbindung ziehen (neu verbinden)
    private enum Endpoint { FROM, TO }
    private boolean draggingEndpoint = false;
    private Endpoint draggingWhich = null;
    private int endpointDragX;
    private int endpointDragY;

    private final JToggleButton connectToggle;
    private final JComboBox<Connection.Style> styleCombo;
    private final JPopupMenu contextMenu;
    private final JPopupMenu connectionContextMenu;
    private int contextX;
    private int contextY;

    private final DrawingPanel drawingPanel;

    private static final int HANDLE_SIZE = 10;
    private static final int HANDLE_HIT = 12;

    public BlockDiagramEditor() {
        setLayout(new BorderLayout());

        drawingPanel = new DrawingPanel();

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
            selectedConnection = null;
            draggingEndpoint = false;
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
            // Stil der ausgewählten Verbindung ändern
            if (selectedConnection != null) {
                selectedConnection.setStyle(currentStyle);
                markDirty();
                drawingPanel.repaint();
            }
        });
        toolbar.add(styleCombo);

        add(toolbar, BorderLayout.NORTH);
        add(new JScrollPane(drawingPanel), BorderLayout.CENTER);

        contextMenu = new JPopupMenu();
        JMenuItem addBlockItem = new JMenuItem("Neuen Block hier einfügen");
        addBlockItem.addActionListener(e -> createBlockAt(contextX, contextY));
        contextMenu.add(addBlockItem);

        connectionContextMenu = new JPopupMenu();
        JMenuItem deleteConnItem = new JMenuItem("Verbindung löschen");
        deleteConnItem.addActionListener(e -> deleteSelectedConnection());
        connectionContextMenu.add(deleteConnItem);

        setFocusable(true);
        drawingPanel.setFocusable(true);
        drawingPanel.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DELETE || e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
                    deleteSelectedConnection();
                }
            }
        });
    }

    private void deleteSelectedConnection() {
        if (diagram != null && selectedConnection != null) {
            diagram.removeConnection(selectedConnection);
            selectedConnection = null;
            draggingEndpoint = false;
            markDirty();
            drawingPanel.repaint();
        }
    }

    private void createBlockAt(int x, int y) {
        if (diagram == null) return;

        String name = JOptionPane.showInputDialog(this, "Name des Blocks:", "Neuer Block", JOptionPane.PLAIN_MESSAGE);
        if (name != null && !name.isBlank()) {
            Block block = new Block(name.trim(), x, y);
            diagram.addBlock(block);
            selectedBlock = block;
            selectedConnection = null;
            markDirty();
            drawingPanel.repaint();
        }
    }

    public void setDiagram(BlockDiagram diagram) {
        this.diagram = diagram;
        selectedBlock = null;
        selectedConnection = null;
        dragging = false;
        connecting = false;
        connectionSource = null;
        draggingEndpoint = false;
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

    private int[] endpoint(Block from, Block toward) {
        Block.PortSide side = from.bestSideToward(toward.centerX(), toward.centerY());
        return from.getPortPoint(side);
    }

    // =====================================================
    // Zeichenfläche
    // =====================================================
    private class DrawingPanel extends JPanel {

        private static final int PORT_SIZE = 8;
        private static final double HIT_TOLERANCE = 8.0;

        public DrawingPanel() {
            setBackground(Color.WHITE);
            setPreferredSize(new Dimension(2000, 1500));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    requestFocusInWindow();

                    if (e.isPopupTrigger()) {
                        handlePopup(e);
                        return;
                    }
                    if (diagram == null) return;

                    // Endpunkt-Handle einer ausgewählten Verbindung?
                    if (selectedConnection != null && !connectMode) {
                        Endpoint handle = hitEndpointHandle(e.getX(), e.getY(), selectedConnection);
                        if (handle != null) {
                            draggingEndpoint = true;
                            draggingWhich = handle;
                            endpointDragX = e.getX();
                            endpointDragY = e.getY();
                            selectedBlock = null;
                            repaint();
                            return;
                        }
                    }

                    if (connectMode) {
                        Block hit = findBlockAt(e.getX(), e.getY());
                        if (hit != null) {
                            connecting = true;
                            connectionSource = hit;
                            tempLineX = e.getX();
                            tempLineY = e.getY();
                            selectedBlock = hit;
                            selectedConnection = null;
                            repaint();
                        }
                        return;
                    }

                    Connection hitConn = findConnectionAt(e.getX(), e.getY());
                    if (hitConn != null) {
                        selectedConnection = hitConn;
                        selectedBlock = null;
                        styleCombo.setSelectedItem(hitConn.getStyle());
                        repaint();
                        return;
                    }

                    handleMousePressed(e);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (e.isPopupTrigger()) {
                        handlePopup(e);
                        return;
                    }

                    // Endpunkt loslassen → neu verbinden
                    if (draggingEndpoint && selectedConnection != null) {
                        Block target = findBlockAt(e.getX(), e.getY());
                        if (target != null) {
                            if (draggingWhich == Endpoint.FROM) {
                                if (target != selectedConnection.getTo()) {
                                    selectedConnection.setFrom(target);
                                    markDirty();
                                }
                            } else {
                                if (target != selectedConnection.getFrom()) {
                                    selectedConnection.setTo(target);
                                    markDirty();
                                }
                            }
                        }
                        draggingEndpoint = false;
                        draggingWhich = null;
                        repaint();
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
                    if (draggingEndpoint) {
                        endpointDragX = e.getX();
                        endpointDragY = e.getY();
                        repaint();
                        return;
                    }
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

        private Endpoint hitEndpointHandle(int px, int py, Connection conn) {
            int[] fromPt = endpoint(conn.getFrom(), conn.getTo());
            int[] toPt = endpoint(conn.getTo(), conn.getFrom());

            if (Math.hypot(px - fromPt[0], py - fromPt[1]) <= HANDLE_HIT) {
                return Endpoint.FROM;
            }
            if (Math.hypot(px - toPt[0], py - toPt[1]) <= HANDLE_HIT) {
                return Endpoint.TO;
            }
            return null;
        }

        private void handlePopup(MouseEvent e) {
            contextX = e.getX();
            contextY = e.getY();

            Connection hitConn = findConnectionAt(e.getX(), e.getY());
            if (hitConn != null) {
                selectedConnection = hitConn;
                selectedBlock = null;
                styleCombo.setSelectedItem(hitConn.getStyle());
                repaint();
                connectionContextMenu.show(this, e.getX(), e.getY());
                return;
            }

            contextMenu.show(this, e.getX(), e.getY());
        }

        private Block findBlockAt(int x, int y) {
            if (diagram == null) return null;
            for (int i = diagram.getBlocks().size() - 1; i >= 0; i--) {
                Block block = diagram.getBlocks().get(i);
                if (block.contains(x, y)) return block;
            }
            return null;
        }

        private Connection findConnectionAt(int px, int py) {
            if (diagram == null) return null;
            for (int i = diagram.getConnections().size() - 1; i >= 0; i--) {
                Connection c = diagram.getConnections().get(i);
                if (hitsConnection(c, px, py)) return c;
            }
            return null;
        }

        private boolean hitsConnection(Connection conn, int px, int py) {
            int[] a = endpoint(conn.getFrom(), conn.getTo());
            int[] b = endpoint(conn.getTo(), conn.getFrom());
            int x1 = a[0], y1 = a[1], x2 = b[0], y2 = b[1];

            return switch (conn.getStyle()) {
                case ORTHOGONAL -> hitsOrthogonal(x1, y1, x2, y2, px, py);
                case BEZIER     -> distanceToBezier(x1, y1, x2, y2, px, py) <= HIT_TOLERANCE;
                default         -> distanceToSegment(x1, y1, x2, y2, px, py) <= HIT_TOLERANCE;
            };
        }

        private boolean hitsOrthogonal(int x1, int y1, int x2, int y2, int px, int py) {
            int midX = (x1 + x2) / 2;
            return distanceToSegment(x1, y1, midX, y1, px, py) <= HIT_TOLERANCE
                    || distanceToSegment(midX, y1, midX, y2, px, py) <= HIT_TOLERANCE
                    || distanceToSegment(midX, y2, x2, y2, px, py) <= HIT_TOLERANCE;
        }

        private double distanceToSegment(int x1, int y1, int x2, int y2, int px, int py) {
            double dx = x2 - x1;
            double dy = y2 - y1;
            if (dx == 0 && dy == 0) {
                return Math.hypot(px - x1, py - y1);
            }
            double t = ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy);
            t = Math.max(0, Math.min(1, t));
            return Math.hypot(px - (x1 + t * dx), py - (y1 + t * dy));
        }

        private double distanceToBezier(int x1, int y1, int x2, int y2, int px, int py) {
            double[] c = bezierControls(x1, y1, x2, y2);
            double min = Double.MAX_VALUE;
            double prevX = x1, prevY = y1;
            for (int i = 1; i <= 20; i++) {
                double t = i / 20.0;
                double xt = bezierPoint(x1, c[0], c[2], x2, t);
                double yt = bezierPoint(y1, c[1], c[3], y2, t);
                min = Math.min(min, distanceToSegment((int) prevX, (int) prevY, (int) xt, (int) yt, px, py));
                prevX = xt;
                prevY = yt;
            }
            return min;
        }

        private double bezierPoint(double p0, double p1, double p2, double p3, double t) {
            double u = 1 - t;
            return u * u * u * p0 + 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t * p3;
        }

        private void handleMousePressed(MouseEvent e) {
            selectedConnection = null;
            draggingEndpoint = false;
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
            if (selectedBlock == null && selectedConnection == null) {
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

            // Verbindungen
            for (Connection conn : diagram.getConnections()) {
                boolean selected = conn == selectedConnection;
                if (selected && draggingEndpoint) {
                    drawConnectionWhileDraggingEndpoint(g2, conn);
                } else {
                    drawConnection(g2, conn, selected);
                }
            }

            // Temporäre neue Verbindung
            if (connecting && connectionSource != null) {
                g2.setColor(new Color(30, 120, 200, 180));
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0,
                        new float[]{8, 6}, 0));
                Block.PortSide side = connectionSource.bestSideToward(tempLineX, tempLineY);
                int[] start = connectionSource.getPortPoint(side);
                drawStyledLine(g2, start[0], start[1], tempLineX, tempLineY, currentStyle);
            }

            // Blöcke + Ports
            for (Block block : diagram.getBlocks()) {
                boolean isSelected = block == selectedBlock;
                boolean highlightAsTarget = draggingEndpoint && block.contains(endpointDragX, endpointDragY);

                g2.setColor(new Color(0, 0, 0, 30));
                g2.fillRoundRect(block.getX() + 3, block.getY() + 3, block.getWidth(), block.getHeight(), 10, 10);

                if (highlightAsTarget) {
                    g2.setColor(new Color(200, 255, 200));
                } else {
                    g2.setColor(isSelected ? new Color(180, 210, 255) : new Color(230, 240, 255));
                }
                g2.fillRoundRect(block.getX(), block.getY(), block.getWidth(), block.getHeight(), 10, 10);

                g2.setColor(highlightAsTarget ? new Color(40, 140, 40)
                        : (isSelected ? new Color(30, 90, 180) : new Color(80, 120, 180)));
                g2.setStroke(new BasicStroke(isSelected || highlightAsTarget ? 2.5f : 1.5f));
                g2.drawRoundRect(block.getX(), block.getY(), block.getWidth(), block.getHeight(), 10, 10);

                g2.setColor(Color.BLACK);
                FontMetrics fm = g2.getFontMetrics();
                int textX = block.getX() + (block.getWidth() - fm.stringWidth(block.getName())) / 2;
                int textY = block.getY() + (block.getHeight() + fm.getAscent()) / 2 - 2;
                g2.drawString(block.getName(), textX, textY);

                if (connectMode || isSelected || draggingEndpoint) {
                    drawPorts(g2, block);
                }
            }

            // Handles der ausgewählten Verbindung
            if (selectedConnection != null && !draggingEndpoint) {
                drawEndpointHandles(g2, selectedConnection);
            }
        }

        private void drawConnectionWhileDraggingEndpoint(Graphics2D g2, Connection conn) {
            int x1, y1, x2, y2;
            if (draggingWhich == Endpoint.FROM) {
                x1 = endpointDragX;
                y1 = endpointDragY;
                int[] to = endpoint(conn.getTo(), conn.getFrom());
                // grob Richtung vom Drag-Punkt
                Block.PortSide side = conn.getTo().bestSideToward(endpointDragX, endpointDragY);
                to = conn.getTo().getPortPoint(side);
                x2 = to[0];
                y2 = to[1];
            } else {
                int[] from = endpoint(conn.getFrom(), conn.getTo());
                Block.PortSide side = conn.getFrom().bestSideToward(endpointDragX, endpointDragY);
                from = conn.getFrom().getPortPoint(side);
                x1 = from[0];
                y1 = from[1];
                x2 = endpointDragX;
                y2 = endpointDragY;
            }

            g2.setColor(new Color(200, 80, 40, 200));
            g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0,
                    new float[]{6, 5}, 0));
            drawStyledLine(g2, x1, y1, x2, y2, conn.getStyle());
        }

        private void drawEndpointHandles(Graphics2D g2, Connection conn) {
            int[] fromPt = endpoint(conn.getFrom(), conn.getTo());
            int[] toPt = endpoint(conn.getTo(), conn.getFrom());

            drawHandle(g2, fromPt[0], fromPt[1], new Color(40, 140, 40));  // Start = grün
            drawHandle(g2, toPt[0], toPt[1], new Color(180, 60, 40));      // Ende = rot
        }

        private void drawHandle(Graphics2D g2, int x, int y, Color color) {
            int r = HANDLE_SIZE / 2;
            g2.setColor(Color.WHITE);
            g2.fillRect(x - r, y - r, HANDLE_SIZE, HANDLE_SIZE);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(2f));
            g2.drawRect(x - r, y - r, HANDLE_SIZE, HANDLE_SIZE);
        }

        private void drawPorts(Graphics2D g2, Block block) {
            g2.setStroke(new BasicStroke(1.2f));
            for (Block.PortSide side : Block.PortSide.values()) {
                int[] p = block.getPortPoint(side);
                int r = PORT_SIZE / 2;
                g2.setColor(Color.WHITE);
                g2.fillOval(p[0] - r, p[1] - r, PORT_SIZE, PORT_SIZE);
                g2.setColor(new Color(30, 90, 180));
                g2.drawOval(p[0] - r, p[1] - r, PORT_SIZE, PORT_SIZE);
            }
        }

        private void drawConnection(Graphics2D g2, Connection conn, boolean selected) {
            int[] a = endpoint(conn.getFrom(), conn.getTo());
            int[] b = endpoint(conn.getTo(), conn.getFrom());
            int x1 = a[0], y1 = a[1], x2 = b[0], y2 = b[1];

            if (selected) {
                g2.setColor(new Color(200, 80, 40));
                g2.setStroke(new BasicStroke(3.2f));
            } else {
                g2.setColor(new Color(60, 60, 60));
                g2.setStroke(new BasicStroke(2.2f));
            }
            drawStyledLine(g2, x1, y1, x2, y2, conn.getStyle());
            drawArrowHead(g2, x1, y1, x2, y2, conn.getStyle());
        }

        private void drawStyledLine(Graphics2D g2, int x1, int y1, int x2, int y2, Connection.Style style) {
            switch (style) {
                case ORTHOGONAL -> drawOrthogonal(g2, x1, y1, x2, y2);
                case BEZIER     -> drawBezier(g2, x1, y1, x2, y2);
                default         -> g2.drawLine(x1, y1, x2, y2);
            }
        }

        private void drawOrthogonal(Graphics2D g2, int x1, int y1, int x2, int y2) {
            int midX = (x1 + x2) / 2;
            Path2D path = new Path2D.Double();
            path.moveTo(x1, y1);
            path.lineTo(midX, y1);
            path.lineTo(midX, y2);
            path.lineTo(x2, y2);
            g2.draw(path);
        }

        private double[] bezierControls(int x1, int y1, int x2, int y2) {
            double dx = x2 - x1;
            double dy = y2 - y1;
            double cx1, cy1, cx2, cy2;
            if (Math.abs(dy) > Math.abs(dx)) {
                cx1 = x1; cy1 = y1 + dy * 0.4;
                cx2 = x2; cy2 = y2 - dy * 0.4;
            } else {
                cx1 = x1 + dx * 0.4; cy1 = y1;
                cx2 = x2 - dx * 0.4; cy2 = y2;
            }
            return new double[]{cx1, cy1, cx2, cy2};
        }

        private void drawBezier(Graphics2D g2, int x1, int y1, int x2, int y2) {
            double[] c = bezierControls(x1, y1, x2, y2);
            g2.draw(new CubicCurve2D.Double(x1, y1, c[0], c[1], c[2], c[3], x2, y2));
        }

        private void drawArrowHead(Graphics2D g2, int x1, int y1, int x2, int y2, Connection.Style style) {
            double angle;
            if (style == Connection.Style.ORTHOGONAL) {
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
