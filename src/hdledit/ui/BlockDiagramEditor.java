// src/main/java/hdledit/ui/BlockDiagramEditor.java
package hdledit.ui;

import hdledit.model.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BlockDiagramEditor extends JPanel {

    private BlockDiagram diagram;
    private Block selectedBlock;
    private Connection selectedConnection;
    private Port selectedPort;
    private int dragOffsetX, dragOffsetY;
    private boolean dragging;
    private Runnable onChangeCallback;

    private boolean connectMode;
    private boolean connecting;
    private Block connectionSource;
    private Port connectionSourcePort;
    private final List<int[]> pendingWaypoints = new ArrayList<>();
    private int tempLineX, tempLineY;
    private Connection.Style currentStyle = Connection.Style.DIRECT;

    private enum Endpoint { FROM, TO }
    private boolean draggingEndpoint;
    private Endpoint draggingWhich;
    private int endpointDragX, endpointDragY;

    private boolean draggingSegment;
    private int draggingSegmentIndex = -1;
    private boolean segmentAxisAligned, segmentIsHorizontal;
    private int segmentLastX, segmentLastY;

    private boolean draggingWaypoint;
    private int draggingWaypointIndex = -1;

    private boolean draggingBezierCtrl;
    private int bezierSegIndex = -1, bezierCtrlWhich;

    private boolean draggingPort;
    private Port draggingPortRef;

    private final JToggleButton connectToggle;
    private final JComboBox<Connection.Style> styleCombo;
    private final JPopupMenu contextMenu;
    private final JPopupMenu connectionContextMenu;
    private final JPopupMenu blockContextMenu;
    private final JMenuItem addHandleItem, deleteHandleItem, segAsLineItem, segAsBezierItem;
    private final JMenuItem addPortItem, deletePortItem;
    private int contextX, contextY;
    private int contextWaypointIndex = -1, contextSegmentIndex = -1;
    private Block contextBlock;
    private Port contextPort;

    private final DrawingPanel drawingPanel;

    private static final int HANDLE_SIZE = 10, HANDLE_HIT = 12, PORT_HIT = 12, CTRL_HIT = 10, EDGE_TOL = 10;

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
        connectToggle.setToolTipText("Verbindungsmodus: freier Port → … → freier Port");
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
        styleCombo.setMaximumSize(new Dimension(130, 28));
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

        // Block-Kontextmenü (Ports)
        blockContextMenu = new JPopupMenu();
        addPortItem = new JMenuItem("Port hier hinzufügen");
        addPortItem.addActionListener(e -> addPortAtContext());
        blockContextMenu.add(addPortItem);
        deletePortItem = new JMenuItem("Port löschen");
        deletePortItem.addActionListener(e -> deletePortAtContext());
        blockContextMenu.add(deletePortItem);

        connectionContextMenu = new JPopupMenu();
        addHandleItem = new JMenuItem("Handle hier hinzufügen");
        addHandleItem.addActionListener(e -> addHandleAtContext());
        connectionContextMenu.add(addHandleItem);
        deleteHandleItem = new JMenuItem("Handle löschen");
        deleteHandleItem.addActionListener(e -> deleteHandleAtContext());
        connectionContextMenu.add(deleteHandleItem);
        connectionContextMenu.addSeparator();
        segAsLineItem = new JMenuItem("Abschnitt als Linie");
        segAsLineItem.addActionListener(e -> setContextSegmentKind(SegmentData.Kind.LINE));
        connectionContextMenu.add(segAsLineItem);
        segAsBezierItem = new JMenuItem("Abschnitt als Bezierkurve");
        segAsBezierItem.addActionListener(e -> setContextSegmentKind(SegmentData.Kind.BEZIER));
        connectionContextMenu.add(segAsBezierItem);
        connectionContextMenu.addSeparator();
        JMenuItem deleteConnItem = new JMenuItem("Verbindung löschen");
        deleteConnItem.addActionListener(e -> deleteSelectedConnection());
        connectionContextMenu.add(deleteConnItem);
        JMenuItem clearWpItem = new JMenuItem("Alle Eckpunkte zurücksetzen");
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
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DELETE || e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
                    if (selectedPort != null && selectedBlock != null) {
                        deletePort(selectedBlock, selectedPort);
                    } else {
                        deleteSelectedConnection();
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    cancelConnecting();
                    drawingPanel.repaint();
                }
            }
        });
    }

    private void addPortAtContext() {
        if (contextBlock == null) return;
        Object[] hit = contextBlock.edgeHit(contextX, contextY, EDGE_TOL + 20);
        if (hit == null) {
            // Fallback: Seite in Richtung Klick
            Block.PortSide side = contextBlock.bestSideToward(contextX, contextY);
            hit = new Object[]{side, 0.5};
        }
        Port port = new Port((Block.PortSide) hit[0], (Double) hit[1]);
        contextBlock.addPort(port);
        selectedPort = port;
        selectedBlock = contextBlock;
        markDirty();
        drawingPanel.repaint();
    }

    private void deletePortAtContext() {
        if (contextBlock != null && contextPort != null) {
            deletePort(contextBlock, contextPort);
        }
    }

    private void deletePort(Block block, Port port) {
        if (diagram != null) diagram.removeConnectionsUsingPort(port);
        block.removePort(port);
        if (selectedPort == port) selectedPort = null;
        markDirty();
        drawingPanel.repaint();
    }

    private void setContextSegmentKind(SegmentData.Kind kind) {
        if (selectedConnection == null || contextSegmentIndex < 0) return;
        selectedConnection.setSegmentKind(contextSegmentIndex, kind);
        if (kind == SegmentData.Kind.BEZIER) initBezierControls(selectedConnection, contextSegmentIndex);
        markDirty();
        drawingPanel.repaint();
    }

    private void initBezierControls(Connection conn, int segIdx) {
        List<int[]> pts = routePoints(conn);
        if (segIdx < 0 || segIdx >= pts.size() - 1) return;
        SegmentData seg = conn.getSegment(segIdx);
        if (seg == null) return;
        int[] a = pts.get(segIdx), b = pts.get(segIdx + 1);
        int dx = b[0] - a[0], dy = b[1] - a[1];
        seg.setC1(a[0] + dx / 3, a[1] + dy / 3);
        seg.setC2(a[0] + 2 * dx / 3, a[1] + 2 * dy / 3);
    }

    private void addHandleAtContext() {
        if (selectedConnection == null) return;
        selectedConnection.insertWaypoint(findInsertWaypointIndex(selectedConnection, contextX, contextY), contextX, contextY);
        markDirty();
        drawingPanel.repaint();
    }

    private void deleteHandleAtContext() {
        if (selectedConnection == null || contextWaypointIndex < 0) return;
        selectedConnection.removeWaypoint(contextWaypointIndex);
        markDirty();
        drawingPanel.repaint();
    }

    private int findInsertWaypointIndex(Connection conn, int x, int y) {
        List<int[]> pts = routePoints(conn);
        int best = 0; double bestD = Double.MAX_VALUE;
        for (int i = 0; i < pts.size() - 1; i++) {
            double d = distSeg(pts.get(i)[0], pts.get(i)[1], pts.get(i+1)[0], pts.get(i+1)[1], x, y);
            if (d < bestD) { bestD = d; best = i; }
        }
        return best;
    }

    private double distSeg(int x1, int y1, int x2, int y2, int px, int py) {
        double dx = x2 - x1, dy = y2 - y1;
        if (dx == 0 && dy == 0) return Math.hypot(px - x1, py - y1);
        double t = Math.max(0, Math.min(1, ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy)));
        return Math.hypot(px - (x1 + t * dx), py - (y1 + t * dy));
    }

    private void cancelConnecting() {
        connecting = false; connectionSource = null; connectionSourcePort = null;
        pendingWaypoints.clear();
        draggingEndpoint = draggingSegment = draggingWaypoint = draggingBezierCtrl = draggingPort = false;
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
        selectedBlock = null; selectedConnection = null; selectedPort = null;
        dragging = false; cancelConnecting();
        connectMode = false; connectToggle.setSelected(false);
        if (diagram != null) for (Block b : diagram.getBlocks()) b.ensureDefaultPorts();
        drawingPanel.repaint();
    }

    public void setOnChangeCallback(Runnable callback) { this.onChangeCallback = callback; }

    private void markDirty() {
        if (diagram != null) {
            diagram.setDirty(true);
            if (onChangeCallback != null) onChangeCallback.run();
        }
    }

    private int[] startPoint(Connection c) {
        return c.getFrom().getPortPoint(c.getFromPort());
    }

    private int[] endPoint(Connection c) {
        return c.getTo().getPortPoint(c.getToPort());
    }

    private List<int[]> routePoints(Connection c) {
        List<int[]> pts = new ArrayList<>();
        pts.add(startPoint(c));
        for (int[] wp : c.getWaypoints()) pts.add(new int[]{wp[0], wp[1]});
        pts.add(endPoint(c));
        return pts;
    }

    private int[] autoBezierControls(int x1, int y1, int x2, int y2) {
        int dx = x2 - x1, dy = y2 - y1;
        return new int[]{x1 + dx/3, y1 + dy/3, x1 + 2*dx/3, y1 + 2*dy/3};
    }

    // ========== DrawingPanel ==========
    private class DrawingPanel extends JPanel {
        private static final double HIT = 8.0;

        public DrawingPanel() {
            setBackground(Color.WHITE);
            setPreferredSize(new Dimension(2000, 1500));

            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    requestFocusInWindow();
                    if (e.isPopupTrigger()) { handlePopup(e); return; }
                    if (diagram == null) return;

                    // Port ziehen?
                    Object[] portHit = findPortAt(e.getX(), e.getY());
                    if (portHit != null && !connectMode) {
                        Block b = (Block) portHit[0];
                        Port p = (Port) portHit[1];
                        selectedPort = p;
                        selectedBlock = b;
                        selectedConnection = null;
                        draggingPort = true;
                        draggingPortRef = p;
                        repaint();
                        return;
                    }

                    if (selectedConnection != null && !connectMode) {
                        int[] ctrlHit = hitBezierControl(e.getX(), e.getY(), selectedConnection);
                        if (ctrlHit != null) {
                            draggingBezierCtrl = true;
                            bezierSegIndex = ctrlHit[0]; bezierCtrlWhich = ctrlHit[1];
                            selectedBlock = null; repaint(); return;
                        }
                        int wpIdx = hitWaypointHandle(e.getX(), e.getY(), selectedConnection);
                        if (wpIdx >= 0) {
                            draggingWaypoint = true; draggingWaypointIndex = wpIdx;
                            selectedBlock = null; repaint(); return;
                        }
                        Endpoint ep = hitEndpointHandle(e.getX(), e.getY(), selectedConnection);
                        if (ep != null) {
                            draggingEndpoint = true; draggingWhich = ep;
                            endpointDragX = e.getX(); endpointDragY = e.getY();
                            selectedBlock = null; repaint(); return;
                        }
                        int seg = hitSegment(e.getX(), e.getY(), selectedConnection);
                        if (seg >= 0) {
                            List<int[]> pts = routePoints(selectedConnection);
                            int[] a = pts.get(seg), b = pts.get(seg+1);
                            draggingSegment = true; draggingSegmentIndex = seg;
                            segmentAxisAligned = a[0]==b[0] || a[1]==b[1];
                            segmentIsHorizontal = a[1]==b[1];
                            segmentLastX = e.getX(); segmentLastY = e.getY();
                            selectedBlock = null; repaint(); return;
                        }
                    }

                    if (connectMode) { handleConnectClick(e); return; }

                    Connection hitConn = findConnectionAt(e.getX(), e.getY());
                    if (hitConn != null) {
                        selectedConnection = hitConn; selectedBlock = null; selectedPort = null;
                        styleCombo.setSelectedItem(hitConn.getStyle());
                        hitConn.ensureSegmentCount();
                        repaint(); return;
                    }

                    selectedConnection = null; selectedPort = null; cancelConnecting();
                    selectedBlock = findBlockAt(e.getX(), e.getY());
                    if (selectedBlock != null) {
                        dragOffsetX = e.getX() - selectedBlock.getX();
                        dragOffsetY = e.getY() - selectedBlock.getY();
                    }
                    repaint();
                }

                @Override public void mouseReleased(MouseEvent e) {
                    if (e.isPopupTrigger()) { handlePopup(e); return; }

                    if (draggingPort && draggingPortRef != null && selectedBlock != null) {
                        Object[] edge = selectedBlock.edgeHit(e.getX(), e.getY(), EDGE_TOL + 30);
                        if (edge != null) {
                            draggingPortRef.setSide((Block.PortSide) edge[0]);
                            draggingPortRef.setPosition((Double) edge[1]);
                            markDirty();
                        }
                        draggingPort = false; draggingPortRef = null; repaint(); return;
                    }
                    if (draggingBezierCtrl) { draggingBezierCtrl = false; markDirty(); repaint(); return; }
                    if (draggingWaypoint) { draggingWaypoint = false; draggingWaypointIndex = -1; markDirty(); repaint(); return; }
                    if (draggingSegment) { draggingSegment = false; markDirty(); repaint(); return; }

                    if (draggingEndpoint && selectedConnection != null) {
                        Object[] portHit = findPortAt(e.getX(), e.getY());
                        if (portHit != null) {
                            Block target = (Block) portHit[0];
                            Port port = (Port) portHit[1];
                            if (!diagram.isPortOccupied(port) || port == selectedConnection.getFromPort() || port == selectedConnection.getToPort()) {
                                if (draggingWhich == Endpoint.FROM && target != selectedConnection.getTo()) {
                                    selectedConnection.setFrom(target);
                                    selectedConnection.setFromPort(port);
                                    markDirty();
                                } else if (draggingWhich == Endpoint.TO && target != selectedConnection.getFrom()) {
                                    selectedConnection.setTo(target);
                                    selectedConnection.setToPort(port);
                                    markDirty();
                                }
                            }
                        }
                        draggingEndpoint = false; draggingWhich = null; repaint(); return;
                    }
                    if (dragging) { dragging = false; markDirty(); }
                }

                @Override public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e) && !connectMode) {
                        if (selectedBlock == null && selectedConnection == null)
                            createBlockAt(e.getX(), e.getY());
                    }
                }
            });

            addMouseMotionListener(new MouseMotionAdapter() {
                @Override public void mouseDragged(MouseEvent e) {
                    if (draggingPort && draggingPortRef != null && selectedBlock != null) {
                        Object[] edge = selectedBlock.edgeHit(e.getX(), e.getY(), EDGE_TOL + 40);
                        if (edge != null) {
                            draggingPortRef.setSide((Block.PortSide) edge[0]);
                            draggingPortRef.setPosition((Double) edge[1]);
                        }
                        repaint(); return;
                    }
                    if (draggingBezierCtrl && selectedConnection != null) {
                        SegmentData seg = selectedConnection.getSegment(bezierSegIndex);
                        if (seg != null) {
                            if (bezierCtrlWhich == 1) seg.setC1(e.getX(), e.getY());
                            else seg.setC2(e.getX(), e.getY());
                        }
                        repaint(); return;
                    }
                    if (draggingWaypoint && selectedConnection != null && draggingWaypointIndex >= 0
                            && draggingWaypointIndex < selectedConnection.getWaypoints().size()) {
                        selectedConnection.setWaypoint(draggingWaypointIndex, e.getX(), e.getY());
                        repaint(); return;
                    }
                    if (draggingSegment && selectedConnection != null) {
                        int dx = e.getX() - segmentLastX, dy = e.getY() - segmentLastY;
                        segmentLastX = e.getX(); segmentLastY = e.getY();
                        moveSegment(selectedConnection, draggingSegmentIndex, dx, dy, segmentAxisAligned, segmentIsHorizontal);
                        repaint(); return;
                    }
                    if (draggingEndpoint) { endpointDragX = e.getX(); endpointDragY = e.getY(); repaint(); return; }
                    if (connecting) { tempLineX = e.getX(); tempLineY = e.getY(); repaint(); return; }
                    if (selectedBlock != null) {
                        dragging = true;
                        selectedBlock.setX(e.getX() - dragOffsetX);
                        selectedBlock.setY(e.getY() - dragOffsetY);
                        repaint();
                    }
                }
                @Override public void mouseMoved(MouseEvent e) {
                    if (connecting) { tempLineX = e.getX(); tempLineY = e.getY(); repaint(); }
                }
            });
        }

        private void moveSegment(Connection conn, int segIndex, int dx, int dy, boolean axis, boolean horiz) {
            if (conn.getWaypoints().isEmpty()) {
                int[] s = startPoint(conn), e = endPoint(conn);
                conn.addWaypoint((s[0]+e[0])/2 + dx, (s[1]+e[1])/2 + dy);
                return;
            }
            if (!axis) { translateRP(conn, segIndex, dx, dy); translateRP(conn, segIndex+1, dx, dy); }
            else if (horiz) { translateRP(conn, segIndex, 0, dy); translateRP(conn, segIndex+1, 0, dy); }
            else { translateRP(conn, segIndex, dx, 0); translateRP(conn, segIndex+1, dx, 0); }
        }

        private void translateRP(Connection conn, int routeIdx, int dx, int dy) {
            int wpCount = conn.getWaypoints().size();
            int last = wpCount + 1;
            if (routeIdx <= 0) {
                if (wpCount == 0) { int[] s = startPoint(conn); conn.addWaypoint(s[0]+dx, s[1]+dy); }
                else { int[] wp = conn.getWaypoints().get(0); conn.setWaypoint(0, wp[0]+dx, wp[1]+dy); }
            } else if (routeIdx >= last) {
                if (wpCount == 0) { int[] e = endPoint(conn); conn.addWaypoint(e[0]+dx, e[1]+dy); }
                else { int i = wpCount-1; int[] wp = conn.getWaypoints().get(i); conn.setWaypoint(i, wp[0]+dx, wp[1]+dy); }
            } else {
                int i = routeIdx - 1; int[] wp = conn.getWaypoints().get(i); conn.setWaypoint(i, wp[0]+dx, wp[1]+dy);
            }
        }

        private void handleConnectClick(MouseEvent e) {
            Object[] portHit = findPortAt(e.getX(), e.getY());
            Set<String> occupied = diagram.occupiedPortIds();

            if (!connecting) {
                Port port = null; Block block = null;
                if (portHit != null) {
                    block = (Block) portHit[0]; port = (Port) portHit[1];
                    if (occupied.contains(port.getId())) {
                        JOptionPane.showMessageDialog(this, "Dieser Port ist bereits belegt.", "Port belegt", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                } else {
                    block = findBlockAt(e.getX(), e.getY());
                    if (block != null) port = block.nearestPortToward(e.getX(), e.getY(), occupied);
                    if (port == null && block != null) {
                        JOptionPane.showMessageDialog(this, "Kein freier Port an diesem Block.", "Kein Port", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                }
                if (block != null && port != null) {
                    connecting = true; connectionSource = block; connectionSourcePort = port;
                    pendingWaypoints.clear(); tempLineX = e.getX(); tempLineY = e.getY();
                    selectedBlock = block; selectedConnection = null; repaint();
                }
                return;
            }

            // Ziel
            Port targetPort = null; Block target = null;
            if (portHit != null) {
                target = (Block) portHit[0]; targetPort = (Port) portHit[1];
            } else {
                target = findBlockAt(e.getX(), e.getY());
                if (target != null) targetPort = target.nearestPortToward(e.getX(), e.getY(), occupied);
            }

            if (target != null && targetPort != null && target != connectionSource) {
                if (occupied.contains(targetPort.getId())) {
                    JOptionPane.showMessageDialog(this, "Ziel-Port ist bereits belegt.", "Port belegt", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                diagram.addConnection(new Connection(
                        connectionSource, connectionSourcePort, target, targetPort, currentStyle));
                // Waypoints aus pending übernehmen
                Connection last = diagram.getConnections().get(diagram.getConnections().size() - 1);
                for (int[] wp : pendingWaypoints) last.addWaypoint(wp[0], wp[1]);
                markDirty();
                cancelConnecting();
                repaint();
                return;
            }

            // Eckpunkt
            if (target == null) {
                pendingWaypoints.add(new int[]{e.getX(), e.getY()});
                tempLineX = e.getX(); tempLineY = e.getY();
                repaint();
            }
        }

        private Object[] findPortAt(int x, int y) {
            if (diagram == null) return null;
            for (int i = diagram.getBlocks().size() - 1; i >= 0; i--) {
                Block b = diagram.getBlocks().get(i);
                for (Port p : b.getPorts()) {
                    int[] pt = b.getPortPoint(p);
                    if (Math.hypot(x - pt[0], y - pt[1]) <= PORT_HIT) return new Object[]{b, p};
                }
            }
            return null;
        }

        private int hitWaypointHandle(int px, int py, Connection conn) {
            List<int[]> wps = conn.getWaypoints();
            for (int i = 0; i < wps.size(); i++)
                if (Math.hypot(px - wps.get(i)[0], py - wps.get(i)[1]) <= HANDLE_HIT) return i;
            return -1;
        }

        private Endpoint hitEndpointHandle(int px, int py, Connection conn) {
            int[] f = startPoint(conn), t = endPoint(conn);
            if (Math.hypot(px - f[0], py - f[1]) <= HANDLE_HIT) return Endpoint.FROM;
            if (Math.hypot(px - t[0], py - t[1]) <= HANDLE_HIT) return Endpoint.TO;
            return null;
        }

        private int[] hitBezierControl(int px, int py, Connection conn) {
            conn.ensureSegmentCount();
            List<int[]> pts = routePoints(conn);
            for (int i = 0; i < conn.getSegments().size(); i++) {
                SegmentData seg = conn.getSegment(i);
                if (seg == null || seg.getKind() != SegmentData.Kind.BEZIER) continue;
                int[] c = resolveControls(seg, pts.get(i), pts.get(i+1));
                if (Math.hypot(px - c[0], py - c[1]) <= CTRL_HIT) return new int[]{i, 1};
                if (Math.hypot(px - c[2], py - c[3]) <= CTRL_HIT) return new int[]{i, 2};
            }
            return null;
        }

        private int[] resolveControls(SegmentData seg, int[] a, int[] b) {
            if (seg.hasControls()) return new int[]{seg.getC1x(), seg.getC1y(), seg.getC2x(), seg.getC2y()};
            return autoBezierControls(a[0], a[1], b[0], b[1]);
        }

        private int hitSegment(int px, int py, Connection conn) {
            List<int[]> pts = routePoints(conn);
            conn.ensureSegmentCount();
            for (int i = 0; i < pts.size() - 1; i++) {
                int[] a = pts.get(i), b = pts.get(i+1);
                SegmentData seg = conn.getSegment(i);
                if (seg != null && seg.getKind() == SegmentData.Kind.BEZIER) {
                    int[] c = resolveControls(seg, a, b);
                    if (distCubic(a[0],a[1],c[0],c[1],c[2],c[3],b[0],b[1], px, py) <= HIT) return i;
                } else if (distSeg(a[0], a[1], b[0], b[1], px, py) <= HIT) return i;
            }
            return -1;
        }

        private double distCubic(int x1,int y1,int cx1,int cy1,int cx2,int cy2,int x2,int y2,int px,int py) {
            double min = Double.MAX_VALUE, prevX = x1, prevY = y1;
            for (int i = 1; i <= 24; i++) {
                double t = i/24.0;
                double xt = bez(x1,cx1,cx2,x2,t), yt = bez(y1,cy1,cy2,y2,t);
                min = Math.min(min, distSeg((int)prevX,(int)prevY,(int)xt,(int)yt,px,py));
                prevX = xt; prevY = yt;
            }
            return min;
        }

        private double bez(double p0,double p1,double p2,double p3,double t) {
            double u = 1-t; return u*u*u*p0 + 3*u*u*t*p1 + 3*u*t*t*p2 + t*t*t*p3;
        }

        private void handlePopup(MouseEvent e) {
            contextX = e.getX(); contextY = e.getY();

            Object[] portHit = findPortAt(e.getX(), e.getY());
            if (portHit != null) {
                contextBlock = (Block) portHit[0];
                contextPort = (Port) portHit[1];
                selectedBlock = contextBlock; selectedPort = contextPort;
                deletePortItem.setEnabled(true);
                addPortItem.setEnabled(true);
                repaint();
                blockContextMenu.show(this, e.getX(), e.getY());
                return;
            }

            Block edgeBlock = findBlockAt(e.getX(), e.getY());
            if (edgeBlock != null && edgeBlock.edgeHit(e.getX(), e.getY(), EDGE_TOL + 5) != null) {
                contextBlock = edgeBlock; contextPort = null;
                selectedBlock = edgeBlock;
                deletePortItem.setEnabled(false);
                addPortItem.setEnabled(true);
                blockContextMenu.show(this, e.getX(), e.getY());
                return;
            }

            Connection hitConn = findConnectionAt(e.getX(), e.getY());
            if (hitConn != null) {
                selectedConnection = hitConn; selectedBlock = null;
                styleCombo.setSelectedItem(hitConn.getStyle());
                hitConn.ensureSegmentCount();
                contextWaypointIndex = hitWaypointHandle(e.getX(), e.getY(), hitConn);
                contextSegmentIndex = hitSegment(e.getX(), e.getY(), hitConn);
                deleteHandleItem.setEnabled(contextWaypointIndex >= 0);
                segAsLineItem.setEnabled(contextSegmentIndex >= 0);
                segAsBezierItem.setEnabled(contextSegmentIndex >= 0);
                repaint();
                connectionContextMenu.show(this, e.getX(), e.getY());
                return;
            }
            contextMenu.show(this, e.getX(), e.getY());
        }

        private Block findBlockAt(int x, int y) {
            if (diagram == null) return null;
            for (int i = diagram.getBlocks().size()-1; i >= 0; i--) {
                Block b = diagram.getBlocks().get(i);
                if (b.contains(x, y)) return b;
            }
            return null;
        }

        private Connection findConnectionAt(int px, int py) {
            if (diagram == null) return null;
            for (int i = diagram.getConnections().size()-1; i >= 0; i--) {
                if (hitSegment(px, py, diagram.getConnections().get(i)) >= 0)
                    return diagram.getConnections().get(i);
            }
            return null;
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (diagram == null) { g2.setColor(Color.GRAY); g2.drawString("Kein Blockdiagramm geladen", 40, 40); return; }

            Set<String> occupied = diagram.occupiedPortIds();

            for (Connection conn : diagram.getConnections()) {
                boolean sel = conn == selectedConnection;
                if (sel && draggingEndpoint) drawDragEndpoint(g2, conn);
                else drawConnection(g2, conn, sel);
            }

            if (connecting && connectionSource != null && connectionSourcePort != null) {
                g2.setColor(new Color(30, 120, 200, 200));
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{8,6}, 0));
                int[] start = connectionSource.getPortPoint(connectionSourcePort);
                List<int[]> pts = new ArrayList<>();
                pts.add(start); pts.addAll(pendingWaypoints); pts.add(new int[]{tempLineX, tempLineY});
                for (int i = 0; i < pts.size()-1; i++)
                    g2.drawLine(pts.get(i)[0], pts.get(i)[1], pts.get(i+1)[0], pts.get(i+1)[1]);
                for (int[] wp : pendingWaypoints) { g2.setColor(new Color(30,120,200)); g2.fillOval(wp[0]-4, wp[1]-4, 8, 8); }
            }

            for (Block block : diagram.getBlocks()) {
                boolean isSel = block == selectedBlock;
                g2.setColor(new Color(0,0,0,30));
                g2.fillRoundRect(block.getX()+3, block.getY()+3, block.getWidth(), block.getHeight(), 10, 10);
                g2.setColor(isSel ? new Color(180,210,255) : new Color(230,240,255));
                g2.fillRoundRect(block.getX(), block.getY(), block.getWidth(), block.getHeight(), 10, 10);
                g2.setColor(isSel ? new Color(30,90,180) : new Color(80,120,180));
                g2.setStroke(new BasicStroke(isSel ? 2.5f : 1.5f));
                g2.drawRoundRect(block.getX(), block.getY(), block.getWidth(), block.getHeight(), 10, 10);
                g2.setColor(Color.BLACK);
                FontMetrics fm = g2.getFontMetrics();
                int tx = block.getX() + (block.getWidth() - fm.stringWidth(block.getName())) / 2;
                int ty = block.getY() + (block.getHeight() + fm.getAscent()) / 2 - 2;
                g2.drawString(block.getName(), tx, ty);

                // Ports immer sichtbar
                for (Port p : block.getPorts()) {
                    int[] pt = block.getPortPoint(p);
                    boolean occ = occupied.contains(p.getId());
                    boolean selP = p == selectedPort;
                    int r = selP ? 6 : 5;
                    g2.setColor(occ ? new Color(180, 80, 80) : Color.WHITE);
                    g2.fillOval(pt[0]-r, pt[1]-r, r*2, r*2);
                    g2.setColor(occ ? new Color(140, 40, 40) : (selP ? new Color(30, 140, 30) : new Color(30, 90, 180)));
                    g2.setStroke(new BasicStroke(selP ? 2f : 1.2f));
                    g2.drawOval(pt[0]-r, pt[1]-r, r*2, r*2);
                }
            }

            if (selectedConnection != null && !draggingEndpoint) {
                drawEndpointHandles(g2, selectedConnection);
                drawWaypointHandles(g2, selectedConnection);
                drawBezierControls(g2, selectedConnection);
            }
        }

        private void drawEndpointHandles(Graphics2D g2, Connection conn) {
            int[] f = startPoint(conn), t = endPoint(conn);
            drawHandle(g2, f[0], f[1], new Color(40,140,40));
            drawHandle(g2, t[0], t[1], new Color(180,60,40));
        }

        private void drawWaypointHandles(Graphics2D g2, Connection conn) {
            for (int[] wp : conn.getWaypoints()) drawHandle(g2, wp[0], wp[1], new Color(120,80,180));
        }

        private void drawBezierControls(Graphics2D g2, Connection conn) {
            conn.ensureSegmentCount();
            List<int[]> pts = routePoints(conn);
            for (int i = 0; i < conn.getSegments().size(); i++) {
                SegmentData seg = conn.getSegment(i);
                if (seg == null || seg.getKind() != SegmentData.Kind.BEZIER) continue;
                int[] a = pts.get(i), b = pts.get(i+1);
                int[] c = resolveControls(seg, a, b);
                g2.setColor(new Color(180,120,40,160));
                g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{4,4}, 0));
                g2.drawLine(a[0], a[1], c[0], c[1]);
                g2.drawLine(b[0], b[1], c[2], c[3]);
                drawCtrl(g2, c[0], c[1]); drawCtrl(g2, c[2], c[3]);
            }
        }

        private void drawCtrl(Graphics2D g2, int x, int y) {
            int s = 5;
            Path2D d = new Path2D.Double();
            d.moveTo(x, y-s); d.lineTo(x+s, y); d.lineTo(x, y+s); d.lineTo(x-s, y); d.closePath();
            g2.setColor(Color.WHITE); g2.fill(d);
            g2.setColor(new Color(200,120,20)); g2.setStroke(new BasicStroke(1.5f)); g2.draw(d);
        }

        private void drawHandle(Graphics2D g2, int x, int y, Color color) {
            int r = HANDLE_SIZE/2;
            g2.setColor(Color.WHITE); g2.fillRect(x-r, y-r, HANDLE_SIZE, HANDLE_SIZE);
            g2.setColor(color); g2.setStroke(new BasicStroke(2f)); g2.drawRect(x-r, y-r, HANDLE_SIZE, HANDLE_SIZE);
        }

        private void drawConnection(Graphics2D g2, Connection conn, boolean selected) {
            List<int[]> pts = routePoints(conn);
            conn.ensureSegmentCount();
            g2.setColor(selected ? new Color(200,80,40) : new Color(60,60,60));
            g2.setStroke(new BasicStroke(selected ? 3.2f : 2.2f));
            for (int i = 0; i < pts.size()-1; i++) {
                int[] a = pts.get(i), b = pts.get(i+1);
                SegmentData seg = conn.getSegment(i);
                boolean bezier = seg != null && seg.getKind() == SegmentData.Kind.BEZIER;
                if (!bezier && conn.getStyle() == Connection.Style.BEZIER && conn.getWaypoints().isEmpty() && i==0)
                    bezier = true;
                if (bezier) {
                    int[] c = (seg != null && seg.getKind() == SegmentData.Kind.BEZIER)
                            ? resolveControls(seg, a, b) : autoBezierControls(a[0],a[1],b[0],b[1]);
                    g2.draw(new CubicCurve2D.Double(a[0],a[1], c[0],c[1], c[2],c[3], b[0],b[1]));
                } else g2.drawLine(a[0], a[1], b[0], b[1]);
            }
            int[] la = pts.get(pts.size()-2), lb = pts.get(pts.size()-1);
            drawArrow(g2, la[0], la[1], lb[0], lb[1]);
        }

        private void drawDragEndpoint(Graphics2D g2, Connection conn) {
            g2.setColor(new Color(200,80,40,200));
            g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{6,5}, 0));
            int x1,y1,x2,y2;
            if (draggingWhich == Endpoint.FROM) { x1=endpointDragX; y1=endpointDragY; int[] t=endPoint(conn); x2=t[0]; y2=t[1]; }
            else { int[] f=startPoint(conn); x1=f[0]; y1=f[1]; x2=endpointDragX; y2=endpointDragY; }
            g2.drawLine(x1,y1,x2,y2);
        }

        private void drawArrow(Graphics2D g2, int x1, int y1, int x2, int y2) {
            double angle = Math.atan2(y2-y1, x2-x1);
            int s = 11;
            Path2D a = new Path2D.Double();
            a.moveTo(x2, y2);
            a.lineTo(x2 - s*Math.cos(angle - Math.PI/6), y2 - s*Math.sin(angle - Math.PI/6));
            a.lineTo(x2 - s*Math.cos(angle + Math.PI/6), y2 - s*Math.sin(angle + Math.PI/6));
            a.closePath(); g2.fill(a);
        }
    }
}
