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
import java.util.ArrayList;
import java.util.List;

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
    private Block.PortSide connectionSourceSide = null;
    private final List<int[]> pendingWaypoints = new ArrayList<>();
    private int tempLineX;
    private int tempLineY;
    private Connection.Style currentStyle = Connection.Style.DIRECT;

    // Endpunkt neu verbinden
    private enum Endpoint { FROM, TO }
    private boolean draggingEndpoint = false;
    private Endpoint draggingWhich = null;
    private int endpointDragX;
    private int endpointDragY;

    // Orthogonales Segment verschieben
    private boolean draggingSegment = false;
    private int draggingSegmentIndex = -1; // Index des Startpunkts des Segments in der Punktliste
    private boolean segmentIsHorizontal = false;

    // Waypoint verschieben
    private boolean draggingWaypoint = false;
    private int draggingWaypointIndex = -1;

    private final JToggleButton connectToggle;
    private final JComboBox<Connection.Style> styleCombo;
    private final JPopupMenu contextMenu;
    private final JPopupMenu connectionContextMenu;
    private int contextX;
    private int contextY;

    private final DrawingPanel drawingPanel;

    private static final int HANDLE_SIZE = 10;
    private static final int HANDLE_HIT = 12;
    private static final int PORT_HIT = 14;

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
        connectToggle.setToolTipText("Verbindungsmodus: Klick Port → (Eckpunkte) → Ziel-Port");
        connectToggle.addActionListener(e -> {
            connectMode = connectToggle.isSelected();
            cancelConnecting();
            selectedConnection = null;
            drawingPanel.repaint();
        });
        toolbar.add(connectToggle);

        toolbar.addSeparator();
        toolbar.add(new JLabel(" Stil: "));
        styleCombo = new JComboBox<>(Connection.Style.values());
        styleCombo.setSelectedItem(Connection.Style.DIRECT);
        styleCombo.setMaximumSize(new Dimension(130, 28));
        styleCombo.setToolTipText("Linienart");
        styleCombo.addActionListener(e -> {
            currentStyle = (Connection.Style) styleCombo.getSelectedItem();
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
        JMenuItem clearWpItem = new JMenuItem("Eckpunkte zurücksetzen");
        clearWpItem.addActionListener(e -> {
            if (selectedConnection != null) {
                selectedConnection.clearWaypoints();
                markDirty();
                drawingPanel.repaint();
            }
        });
        connectionContextMenu.add(clearWpItem);

        setFocusable(true);
        drawingPanel.setFocusable(true);
        drawingPanel.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DELETE || e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
                    deleteSelectedConnection();
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    cancelConnecting();
                    drawingPanel.repaint();
                }
            }
        });
    }

    private void cancelConnecting() {
        connecting = false;
        connectionSource = null;
        connectionSourceSide = null;
        pendingWaypoints.clear();
        draggingEndpoint = false;
        draggingSegment = false;
        draggingWaypoint = false;
    }

    private void deleteSelectedConnection() {
        if (diagram != null && selectedConnection != null) {
            diagram.removeConnection(selectedConnection);
            selectedConnection = null;
            cancelConnecting();
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
        cancelConnecting();
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
            if (onChangeCallback != null) onChangeCallback.run();
        }
    }

    private int[] portPoint(Block block, Block.PortSide side, Block toward) {
        if (side != null) return block.getPortPoint(side);
        Block.PortSide auto = block.bestSideToward(toward.centerX(), toward.centerY());
        return block.getPortPoint(auto);
    }

    private int[] startPoint(Connection c) {
        return portPoint(c.getFrom(), c.getFromSide(), c.getTo());
    }

    private int[] endPoint(Connection c) {
        return portPoint(c.getTo(), c.getToSide(), c.getFrom());
    }

    /** Vollständige Punktliste einer Verbindung: Start + Waypoints + Ende */
    private List<int[]> routePoints(Connection c) {
        List<int[]> pts = new ArrayList<>();
        pts.add(startPoint(c));
        for (int[] wp : c.getWaypoints()) {
            pts.add(new int[]{wp[0], wp[1]});
        }
        pts.add(endPoint(c));
        return pts;
    }

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
                    if (e.isPopupTrigger()) { handlePopup(e); return; }
                    if (diagram == null) return;

                    // Waypoint-Handle?
                    if (selectedConnection != null && !connectMode) {
                        int wpIdx = hitWaypointHandle(e.getX(), e.getY(), selectedConnection);
                        if (wpIdx >= 0) {
                            draggingWaypoint = true;
                            draggingWaypointIndex = wpIdx;
                            selectedBlock = null;
                            repaint();
                            return;
                        }

                        // Endpunkt-Handle?
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

                        // Orthogonales Segment?
                        if (selectedConnection.getStyle() == Connection.Style.ORTHOGONAL) {
                            int seg = hitOrthogonalSegment(e.getX(), e.getY(), selectedConnection);
                            if (seg >= 0) {
                                List<int[]> pts = routePoints(selectedConnection);
                                draggingSegment = true;
                                draggingSegmentIndex = seg;
                                segmentIsHorizontal = pts.get(seg)[1] == pts.get(seg + 1)[1];
                                selectedBlock = null;
                                repaint();
                                return;
                            }
                        }
                    }

                    if (connectMode) {
                        handleConnectClick(e);
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
                    if (e.isPopupTrigger()) { handlePopup(e); return; }

                    if (draggingWaypoint) {
                        draggingWaypoint = false;
                        draggingWaypointIndex = -1;
                        markDirty();
                        repaint();
                        return;
                    }

                    if (draggingSegment) {
                        draggingSegment = false;
                        draggingSegmentIndex = -1;
                        markDirty();
                        repaint();
                        return;
                    }

                    if (draggingEndpoint && selectedConnection != null) {
                        Object[] portHit = findPortAt(e.getX(), e.getY());
                        if (portHit != null) {
                            Block target = (Block) portHit[0];
                            Block.PortSide side = (Block.PortSide) portHit[1];
                            if (draggingWhich == Endpoint.FROM && target != selectedConnection.getTo()) {
                                selectedConnection.setFrom(target);
                                selectedConnection.setFromSide(side);
                                markDirty();
                            } else if (draggingWhich == Endpoint.TO && target != selectedConnection.getFrom()) {
                                selectedConnection.setTo(target);
                                selectedConnection.setToSide(side);
                                markDirty();
                            }
                        } else {
                            // Nur Seite am gleichen Block wechseln, wenn nah an Port
                            Block block = draggingWhich == Endpoint.FROM
                                    ? selectedConnection.getFrom()
                                    : selectedConnection.getTo();
                            Block.PortSide side = nearestPortSide(block, e.getX(), e.getY());
                            if (side != null) {
                                if (draggingWhich == Endpoint.FROM) selectedConnection.setFromSide(side);
                                else selectedConnection.setToSide(side);
                                markDirty();
                            }
                        }
                        draggingEndpoint = false;
                        draggingWhich = null;
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
                        if (selectedBlock == null && selectedConnection == null) {
                            createBlockAt(e.getX(), e.getY());
                        }
                    }
                }
            });

            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseDragged(MouseEvent e) {
                    if (draggingWaypoint && selectedConnection != null
                            && draggingWaypointIndex >= 0
                            && draggingWaypointIndex < selectedConnection.getWaypoints().size()) {
                        selectedConnection.setWaypoint(draggingWaypointIndex, e.getX(), e.getY());
                        repaint();
                        return;
                    }

                    if (draggingSegment && selectedConnection != null) {
                        moveOrthogonalSegment(selectedConnection, draggingSegmentIndex, segmentIsHorizontal, e.getX(), e.getY());
                        repaint();
                        return;
                    }

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

                    if (selectedBlock != null) {
                        dragging = true;
                        selectedBlock.setX(e.getX() - dragOffsetX);
                        selectedBlock.setY(e.getY() - dragOffsetY);
                        repaint();
                    }
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    if (connecting) {
                        tempLineX = e.getX();
                        tempLineY = e.getY();
                        repaint();
                    }
                }
            });
        }

        private void handleConnectClick(MouseEvent e) {
            Object[] portHit = findPortAt(e.getX(), e.getY());

            if (!connecting) {
                // Start: Port oder Block
                if (portHit != null) {
                    connecting = true;
                    connectionSource = (Block) portHit[0];
                    connectionSourceSide = (Block.PortSide) portHit[1];
                    pendingWaypoints.clear();
                    tempLineX = e.getX();
                    tempLineY = e.getY();
                    selectedBlock = connectionSource;
                    selectedConnection = null;
                    repaint();
                } else {
                    Block hit = findBlockAt(e.getX(), e.getY());
                    if (hit != null) {
                        connecting = true;
                        connectionSource = hit;
                        connectionSourceSide = hit.bestSideToward(e.getX(), e.getY());
                        pendingWaypoints.clear();
                        tempLineX = e.getX();
                        tempLineY = e.getY();
                        selectedBlock = hit;
                        selectedConnection = null;
                        repaint();
                    }
                }
                return;
            }

            // Bereits am Verbinden: Ziel-Port/Block oder Eckpunkt
            if (portHit != null) {
                Block target = (Block) portHit[0];
                Block.PortSide targetSide = (Block.PortSide) portHit[1];
                if (target != connectionSource) {
                    finishConnection(target, targetSide);
                }
                return;
            }

            Block hit = findBlockAt(e.getX(), e.getY());
            if (hit != null && hit != connectionSource) {
                Block.PortSide targetSide = hit.bestSideToward(
                        connectionSource.centerX(), connectionSource.centerY());
                finishConnection(hit, targetSide);
                return;
            }

            // Leere Fläche → Eckpunkt (vor allem für ORTHOGONAL sinnvoll)
            if (currentStyle == Connection.Style.ORTHOGONAL || currentStyle == Connection.Style.DIRECT) {
                pendingWaypoints.add(new int[]{e.getX(), e.getY()});
                tempLineX = e.getX();
                tempLineY = e.getY();
                repaint();
            }
        }

        private void finishConnection(Block target, Block.PortSide targetSide) {
            boolean exists = diagram.getConnections().stream()
                    .anyMatch(c -> c.getFrom() == connectionSource && c.getTo() == target);
            if (!exists) {
                Connection conn = new Connection(
                        java.util.UUID.randomUUID().toString(),
                        connectionSource, target, currentStyle,
                        connectionSourceSide, targetSide, pendingWaypoints);
                diagram.addConnection(conn);
                markDirty();
            }
            cancelConnecting();
            repaint();
        }

        private Object[] findPortAt(int x, int y) {
            if (diagram == null) return null;
            for (int i = diagram.getBlocks().size() - 1; i >= 0; i--) {
                Block b = diagram.getBlocks().get(i);
                for (Block.PortSide side : Block.PortSide.values()) {
                    int[] p = b.getPortPoint(side);
                    if (Math.hypot(x - p[0], y - p[1]) <= PORT_HIT) {
                        return new Object[]{b, side};
                    }
                }
            }
            return null;
        }

        private Block.PortSide nearestPortSide(Block block, int x, int y) {
            Block.PortSide best = null;
            double bestDist = PORT_HIT * 2;
            for (Block.PortSide side : Block.PortSide.values()) {
                int[] p = block.getPortPoint(side);
                double d = Math.hypot(x - p[0], y - p[1]);
                if (d < bestDist) {
                    bestDist = d;
                    best = side;
                }
            }
            return best;
        }

        private void moveOrthogonalSegment(Connection conn, int segIndex, boolean horizontal, int mx, int my) {
            // Punktliste: 0=start, 1..n-2=waypoints, n-1=end
            List<int[]> pts = routePoints(conn);
            if (segIndex < 0 || segIndex >= pts.size() - 1) return;

            // Wir verschieben das Segment senkrecht: horizontales Segment → Y ändern, vertikales → X
            // Waypoints anpassen; Start/Ende sind an Ports gebunden und werden nicht verschoben,
            // stattdessen neue Waypoints erzeugt falls nötig.

            int wpCount = conn.getWaypoints().size();
            // Indices in waypoints-Liste: route index 1 .. wpCount map to waypoints 0 .. wpCount-1

            if (horizontal) {
                // Y der beiden Enden des Segments auf my setzen (nur Waypoints)
                adjustPointY(conn, pts, segIndex, my);
                adjustPointY(conn, pts, segIndex + 1, my);
            } else {
                adjustPointX(conn, pts, segIndex, mx);
                adjustPointX(conn, pts, segIndex + 1, mx);
            }
        }

        private void adjustPointY(Connection conn, List<int[]> pts, int routeIdx, int y) {
            if (routeIdx <= 0 || routeIdx >= pts.size() - 1) {
                // Start oder Ende → Waypoint einfügen
                ensureWaypointForEndpoint(conn, routeIdx, pts.get(routeIdx)[0], y);
            } else {
                int wpIdx = routeIdx - 1;
                conn.setWaypoint(wpIdx, conn.getWaypoints().get(wpIdx)[0], y);
            }
        }

        private void adjustPointX(Connection conn, List<int[]> pts, int routeIdx, int x) {
            if (routeIdx <= 0 || routeIdx >= pts.size() - 1) {
                ensureWaypointForEndpoint(conn, routeIdx, x, pts.get(routeIdx)[1]);
            } else {
                int wpIdx = routeIdx - 1;
                conn.setWaypoint(wpIdx, x, conn.getWaypoints().get(wpIdx)[1]);
            }
        }

        private void ensureWaypointForEndpoint(Connection conn, int routeIdx, int x, int y) {
            // Einfache Strategie: wenn noch keine Waypoints, einen in der Mitte anlegen
            if (conn.getWaypoints().isEmpty()) {
                int[] s = startPoint(conn);
                int[] e = endPoint(conn);
                conn.addWaypoint((s[0] + e[0]) / 2, (s[1] + e[1]) / 2);
            }
            if (routeIdx <= 0 && !conn.getWaypoints().isEmpty()) {
                conn.setWaypoint(0, x, y);
            } else if (!conn.getWaypoints().isEmpty()) {
                int last = conn.getWaypoints().size() - 1;
                conn.setWaypoint(last, x, y);
            }
        }

        private int hitWaypointHandle(int px, int py, Connection conn) {
            List<int[]> wps = conn.getWaypoints();
            for (int i = 0; i < wps.size(); i++) {
                int[] wp = wps.get(i);
                if (Math.hypot(px - wp[0], py - wp[1]) <= HANDLE_HIT) return i;
            }
            return -1;
        }

        private Endpoint hitEndpointHandle(int px, int py, Connection conn) {
            int[] fromPt = startPoint(conn);
            int[] toPt = endPoint(conn);
            if (Math.hypot(px - fromPt[0], py - fromPt[1]) <= HANDLE_HIT) return Endpoint.FROM;
            if (Math.hypot(px - toPt[0], py - toPt[1]) <= HANDLE_HIT) return Endpoint.TO;
            return null;
        }

        private int hitOrthogonalSegment(int px, int py, Connection conn) {
            List<int[]> pts = routePoints(conn);
            for (int i = 0; i < pts.size() - 1; i++) {
                int[] a = pts.get(i);
                int[] b = pts.get(i + 1);
                if (distanceToSegment(a[0], a[1], b[0], b[1], px, py) <= HIT_TOLERANCE) {
                    return i;
                }
            }
            return -1;
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
            if (conn.getStyle() == Connection.Style.ORTHOGONAL || !conn.getWaypoints().isEmpty()) {
                List<int[]> pts = routePoints(conn);
                for (int i = 0; i < pts.size() - 1; i++) {
                    int[] a = pts.get(i);
                    int[] b = pts.get(i + 1);
                    if (distanceToSegment(a[0], a[1], b[0], b[1], px, py) <= HIT_TOLERANCE) return true;
                }
                return false;
            }
            int[] a = startPoint(conn);
            int[] b = endPoint(conn);
            if (conn.getStyle() == Connection.Style.BEZIER) {
                return distanceToBezier(a[0], a[1], b[0], b[1], px, py) <= HIT_TOLERANCE;
            }
            return distanceToSegment(a[0], a[1], b[0], b[1], px, py) <= HIT_TOLERANCE;
        }

        private double distanceToSegment(int x1, int y1, int x2, int y2, int px, int py) {
            double dx = x2 - x1, dy = y2 - y1;
            if (dx == 0 && dy == 0) return Math.hypot(px - x1, py - y1);
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
                prevX = xt; prevY = yt;
            }
            return min;
        }

        private double bezierPoint(double p0, double p1, double p2, double p3, double t) {
            double u = 1 - t;
            return u*u*u*p0 + 3*u*u*t*p1 + 3*u*t*t*p2 + t*t*t*p3;
        }

        private void handleMousePressed(MouseEvent e) {
            selectedConnection = null;
            cancelConnecting();
            selectedBlock = findBlockAt(e.getX(), e.getY());
            if (selectedBlock != null) {
                dragOffsetX = e.getX() - selectedBlock.getX();
                dragOffsetY = e.getY() - selectedBlock.getY();
            }
            repaint();
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

            for (Connection conn : diagram.getConnections()) {
                boolean selected = conn == selectedConnection;
                if (selected && draggingEndpoint) {
                    drawConnectionDraggingEndpoint(g2, conn);
                } else {
                    drawConnection(g2, conn, selected);
                }
            }

            // Pending-Verbindung zeichnen
            if (connecting && connectionSource != null) {
                g2.setColor(new Color(30, 120, 200, 200));
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0,
                        new float[]{8, 6}, 0));

                int[] start = connectionSourceSide != null
                        ? connectionSource.getPortPoint(connectionSourceSide)
                        : connectionSource.getPortPoint(connectionSource.bestSideToward(tempLineX, tempLineY));

                List<int[]> pts = new ArrayList<>();
                pts.add(start);
                pts.addAll(pendingWaypoints);
                pts.add(new int[]{tempLineX, tempLineY});

                for (int i = 0; i < pts.size() - 1; i++) {
                    int[] a = pts.get(i);
                    int[] b = pts.get(i + 1);
                    if (currentStyle == Connection.Style.ORTHOGONAL) {
                        drawOrthogonal(g2, a[0], a[1], b[0], b[1]);
                    } else {
                        g2.drawLine(a[0], a[1], b[0], b[1]);
                    }
                }

                // Pending-Eckpunkte markieren
                for (int[] wp : pendingWaypoints) {
                    g2.setColor(new Color(30, 120, 200));
                    g2.fillOval(wp[0] - 4, wp[1] - 4, 8, 8);
                }
            }

            for (Block block : diagram.getBlocks()) {
                boolean isSelected = block == selectedBlock;
                boolean highlight = draggingEndpoint && block.contains(endpointDragX, endpointDragY);

                g2.setColor(new Color(0, 0, 0, 30));
                g2.fillRoundRect(block.getX() + 3, block.getY() + 3, block.getWidth(), block.getHeight(), 10, 10);

                g2.setColor(highlight ? new Color(200, 255, 200)
                        : (isSelected ? new Color(180, 210, 255) : new Color(230, 240, 255)));
                g2.fillRoundRect(block.getX(), block.getY(), block.getWidth(), block.getHeight(), 10, 10);

                g2.setColor(highlight ? new Color(40, 140, 40)
                        : (isSelected ? new Color(30, 90, 180) : new Color(80, 120, 180)));
                g2.setStroke(new BasicStroke(isSelected || highlight ? 2.5f : 1.5f));
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

            if (selectedConnection != null && !draggingEndpoint) {
                drawEndpointHandles(g2, selectedConnection);
                drawWaypointHandles(g2, selectedConnection);
            }
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

        private void drawEndpointHandles(Graphics2D g2, Connection conn) {
            int[] fromPt = startPoint(conn);
            int[] toPt = endPoint(conn);
            drawHandle(g2, fromPt[0], fromPt[1], new Color(40, 140, 40));
            drawHandle(g2, toPt[0], toPt[1], new Color(180, 60, 40));
        }

        private void drawWaypointHandles(Graphics2D g2, Connection conn) {
            for (int[] wp : conn.getWaypoints()) {
                drawHandle(g2, wp[0], wp[1], new Color(120, 80, 180));
            }
        }

        private void drawHandle(Graphics2D g2, int x, int y, Color color) {
            int r = HANDLE_SIZE / 2;
            g2.setColor(Color.WHITE);
            g2.fillRect(x - r, y - r, HANDLE_SIZE, HANDLE_SIZE);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(2f));
            g2.drawRect(x - r, y - r, HANDLE_SIZE, HANDLE_SIZE);
        }

        private void drawConnection(Graphics2D g2, Connection conn, boolean selected) {
            List<int[]> pts = routePoints(conn);
            g2.setColor(selected ? new Color(200, 80, 40) : new Color(60, 60, 60));
            g2.setStroke(new BasicStroke(selected ? 3.2f : 2.2f));

            if (conn.getStyle() == Connection.Style.BEZIER && conn.getWaypoints().isEmpty()) {
                int[] a = pts.get(0);
                int[] b = pts.get(pts.size() - 1);
                drawBezier(g2, a[0], a[1], b[0], b[1]);
                drawArrowHead(g2, a[0], a[1], b[0], b[1], conn.getStyle());
                return;
            }

            for (int i = 0; i < pts.size() - 1; i++) {
                int[] a = pts.get(i);
                int[] b = pts.get(i + 1);
                if (conn.getStyle() == Connection.Style.ORTHOGONAL) {
                    // bereits punktweise – direkte Linien (User-Ecken sind gesetzt)
                    g2.drawLine(a[0], a[1], b[0], b[1]);
                } else if (conn.getStyle() == Connection.Style.DIRECT || !conn.getWaypoints().isEmpty()) {
                    g2.drawLine(a[0], a[1], b[0], b[1]);
                }
            }
            int[] lastA = pts.get(pts.size() - 2);
            int[] lastB = pts.get(pts.size() - 1);
            drawArrowHead(g2, lastA[0], lastA[1], lastB[0], lastB[1], Connection.Style.DIRECT);
        }

        private void drawConnectionDraggingEndpoint(Graphics2D g2, Connection conn) {
            g2.setColor(new Color(200, 80, 40, 200));
            g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0,
                    new float[]{6, 5}, 0));
            int x1, y1, x2, y2;
            if (draggingWhich == Endpoint.FROM) {
                x1 = endpointDragX; y1 = endpointDragY;
                int[] to = endPoint(conn);
                x2 = to[0]; y2 = to[1];
            } else {
                int[] from = startPoint(conn);
                x1 = from[0]; y1 = from[1];
                x2 = endpointDragX; y2 = endpointDragY;
            }
            g2.drawLine(x1, y1, x2, y2);
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
            double dx = x2 - x1, dy = y2 - y1;
            if (Math.abs(dy) > Math.abs(dx)) {
                return new double[]{x1, y1 + dy * 0.4, x2, y2 - dy * 0.4};
            }
            return new double[]{x1 + dx * 0.4, y1, x2 - dx * 0.4, y2};
        }

        private void drawBezier(Graphics2D g2, int x1, int y1, int x2, int y2) {
            double[] c = bezierControls(x1, y1, x2, y2);
            g2.draw(new CubicCurve2D.Double(x1, y1, c[0], c[1], c[2], c[3], x2, y2));
        }

        private void drawArrowHead(Graphics2D g2, int x1, int y1, int x2, int y2, Connection.Style style) {
            double angle = Math.atan2(y2 - y1, x2 - x1);
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
