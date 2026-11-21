// src/main/java/hdledit/ui/BlockDiagramEditor.java
package hdledit.ui;

import hdledit.model.Block;
import hdledit.model.BlockDiagram;
import hdledit.model.Connection;
import hdledit.model.SegmentData;

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
    private int dragOffsetX, dragOffsetY;
    private boolean dragging = false;
    private Runnable onChangeCallback;

    private boolean connectMode = false;
    private boolean connecting = false;
    private Block connectionSource = null;
    private Block.PortSide connectionSourceSide = null;
    private final List<int[]> pendingWaypoints = new ArrayList<>();
    private int tempLineX, tempLineY;
    private Connection.Style currentStyle = Connection.Style.DIRECT;

    private enum Endpoint { FROM, TO }
    private boolean draggingEndpoint = false;
    private Endpoint draggingWhich = null;
    private int endpointDragX, endpointDragY;

    private boolean draggingSegment = false;
    private int draggingSegmentIndex = -1;
    private boolean segmentAxisAligned = false;
    private boolean segmentIsHorizontal = false;
    private int segmentLastX, segmentLastY;

    private boolean draggingWaypoint = false;
    private int draggingWaypointIndex = -1;

    // Bezier-Kontrollpunkt ziehen: segmentIndex + which (1 oder 2)
    private boolean draggingBezierCtrl = false;
    private int bezierSegIndex = -1;
    private int bezierCtrlWhich = 0; // 1 oder 2

    private final JToggleButton connectToggle;
    private final JComboBox<Connection.Style> styleCombo;
    private final JPopupMenu contextMenu;
    private final JPopupMenu connectionContextMenu;
    private final JMenuItem addHandleItem;
    private final JMenuItem deleteHandleItem;
    private final JMenuItem segAsLineItem;
    private final JMenuItem segAsBezierItem;
    private int contextX, contextY;
    private int contextWaypointIndex = -1;
    private int contextSegmentIndex = -1;

    private final DrawingPanel drawingPanel;

    private static final int HANDLE_SIZE = 10;
    private static final int HANDLE_HIT = 12;
    private static final int PORT_HIT = 14;
    private static final int CTRL_HIT = 10;

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
        connectToggle.setToolTipText("Verbindungsmodus");
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

    private void setContextSegmentKind(SegmentData.Kind kind) {
        if (selectedConnection == null || contextSegmentIndex < 0) return;
        selectedConnection.setSegmentKind(contextSegmentIndex, kind);
        if (kind == SegmentData.Kind.BEZIER) {
            initBezierControls(selectedConnection, contextSegmentIndex);
        }
        markDirty();
        drawingPanel.repaint();
    }

    private void initBezierControls(Connection conn, int segIdx) {
        List<int[]> pts = routePoints(conn);
        if (segIdx < 0 || segIdx >= pts.size() - 1) return;
        SegmentData seg = conn.getSegment(segIdx);
        if (seg == null) return;
        int[] a = pts.get(segIdx);
        int[] b = pts.get(segIdx + 1);
        int dx = b[0] - a[0];
        int dy = b[1] - a[1];
        seg.setC1(a[0] + dx / 3, a[1] + dy / 3);
        seg.setC2(a[0] + 2 * dx / 3, a[1] + 2 * dy / 3);
    }

    private void addHandleAtContext() {
        if (selectedConnection == null) return;
        int insertIdx = findInsertWaypointIndex(selectedConnection, contextX, contextY);
        selectedConnection.insertWaypoint(insertIdx, contextX, contextY);
        markDirty();
        drawingPanel.repaint();
    }

    private void deleteHandleAtContext() {
        if (selectedConnection == null || contextWaypointIndex < 0) return;
        selectedConnection.removeWaypoint(contextWaypointIndex);
        contextWaypointIndex = -1;
        markDirty();
        drawingPanel.repaint();
    }

    private int findInsertWaypointIndex(Connection conn, int x, int y) {
        List<int[]> pts = routePoints(conn);
        int bestSeg = 0;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < pts.size() - 1; i++) {
            int[] a = pts.get(i);
            int[] b = pts.get(i + 1);
            double d = distanceToSegment(a[0], a[1], b[0], b[1], x, y);
            if (d < bestDist) {
                bestDist = d;
                bestSeg = i;
            }
        }
        return bestSeg;
    }

    private double distanceToSegment(int x1, int y1, int x2, int y2, int px, int py) {
        double dx = x2 - x1, dy = y2 - y1;
        if (dx == 0 && dy == 0) return Math.hypot(px - x1, py - y1);
        double t = ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy);
        t = Math.max(0, Math.min(1, t));
        return Math.hypot(px - (x1 + t * dx), py - (y1 + t * dy));
    }

    private void cancelConnecting() {
        connecting = false;
        connectionSource = null;
        connectionSourceSide = null;
        pendingWaypoints.clear();
        draggingEndpoint = false;
        draggingSegment = false;
        draggingWaypoint = false;
        draggingBezierCtrl = false;
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
        return block.getPortPoint(block.bestSideToward(toward.centerX(), toward.centerY()));
    }

    private int[] startPoint(Connection c) {
        return portPoint(c.getFrom(), c.getFromSide(), c.getTo());
    }

    private int[] endPoint(Connection c) {
        return portPoint(c.getTo(), c.getToSide(), c.getFrom());
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
        return new int[]{
                x1 + dx / 3, y1 + dy / 3,
                x1 + 2 * dx / 3, y1 + 2 * dy / 3
        };
    }

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

                    if (selectedConnection != null && !connectMode) {
                        // Bezier-Kontrollpunkt?
                        int[] ctrlHit = hitBezierControl(e.getX(), e.getY(), selectedConnection);
                        if (ctrlHit != null) {
                            draggingBezierCtrl = true;
                            bezierSegIndex = ctrlHit[0];
                            bezierCtrlWhich = ctrlHit[1];
                            selectedBlock = null;
                            repaint();
                            return;
                        }

                        int wpIdx = hitWaypointHandle(e.getX(), e.getY(), selectedConnection);
                        if (wpIdx >= 0) {
                            draggingWaypoint = true;
                            draggingWaypointIndex = wpIdx;
                            selectedBlock = null;
                            repaint();
                            return;
                        }

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

                        int seg = hitSegment(e.getX(), e.getY(), selectedConnection);
                        if (seg >= 0) {
                            List<int[]> pts = routePoints(selectedConnection);
                            int[] a = pts.get(seg);
                            int[] b = pts.get(seg + 1);
                            draggingSegment = true;
                            draggingSegmentIndex = seg;
                            segmentAxisAligned = (a[0] == b[0]) || (a[1] == b[1]);
                            segmentIsHorizontal = a[1] == b[1];
                            segmentLastX = e.getX();
                            segmentLastY = e.getY();
                            selectedBlock = null;
                            repaint();
                            return;
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
                        hitConn.ensureSegmentCount();
                        repaint();
                        return;
                    }

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
                public void mouseReleased(MouseEvent e) {
                    if (e.isPopupTrigger()) { handlePopup(e); return; }

                    if (draggingBezierCtrl) {
                        draggingBezierCtrl = false;
                        bezierSegIndex = -1;
                        markDirty();
                        repaint();
                        return;
                    }
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
                            Block block = draggingWhich == Endpoint.FROM
                                    ? selectedConnection.getFrom() : selectedConnection.getTo();
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
                    if (draggingBezierCtrl && selectedConnection != null) {
                        SegmentData seg = selectedConnection.getSegment(bezierSegIndex);
                        if (seg != null) {
                            if (bezierCtrlWhich == 1) seg.setC1(e.getX(), e.getY());
                            else seg.setC2(e.getX(), e.getY());
                        }
                        repaint();
                        return;
                    }
                    if (draggingWaypoint && selectedConnection != null
                            && draggingWaypointIndex >= 0
                            && draggingWaypointIndex < selectedConnection.getWaypoints().size()) {
                        selectedConnection.setWaypoint(draggingWaypointIndex, e.getX(), e.getY());
                        repaint();
                        return;
                    }
                    if (draggingSegment && selectedConnection != null) {
                        int dx = e.getX() - segmentLastX;
                        int dy = e.getY() - segmentLastY;
                        segmentLastX = e.getX();
                        segmentLastY = e.getY();
                        moveSegment(selectedConnection, draggingSegmentIndex, dx, dy,
                                segmentAxisAligned, segmentIsHorizontal);
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

        private void moveSegment(Connection conn, int segIndex, int dx, int dy,
                                 boolean axisAligned, boolean horizontal) {
            if (conn.getWaypoints().isEmpty()) {
                int[] s = startPoint(conn);
                int[] e = endPoint(conn);
                conn.addWaypoint((s[0] + e[0]) / 2 + dx, (s[1] + e[1]) / 2 + dy);
                return;
            }
            if (!axisAligned) {
                translateRoutePoint(conn, segIndex, dx, dy);
                translateRoutePoint(conn, segIndex + 1, dx, dy);
            } else if (horizontal) {
                translateRoutePoint(conn, segIndex, 0, dy);
                translateRoutePoint(conn, segIndex + 1, 0, dy);
            } else {
                translateRoutePoint(conn, segIndex, dx, 0);
                translateRoutePoint(conn, segIndex + 1, dx, 0);
            }
        }

        private void translateRoutePoint(Connection conn, int routeIdx, int dx, int dy) {
            int wpCount = conn.getWaypoints().size();
            int lastRoute = wpCount + 1;
            if (routeIdx <= 0) {
                if (wpCount == 0) {
                    int[] s = startPoint(conn);
                    conn.addWaypoint(s[0] + dx, s[1] + dy);
                } else {
                    int[] wp = conn.getWaypoints().get(0);
                    conn.setWaypoint(0, wp[0] + dx, wp[1] + dy);
                }
            } else if (routeIdx >= lastRoute) {
                if (wpCount == 0) {
                    int[] e = endPoint(conn);
                    conn.addWaypoint(e[0] + dx, e[1] + dy);
                } else {
                    int last = wpCount - 1;
                    int[] wp = conn.getWaypoints().get(last);
                    conn.setWaypoint(last, wp[0] + dx, wp[1] + dy);
                }
            } else {
                int wpIdx = routeIdx - 1;
                int[] wp = conn.getWaypoints().get(wpIdx);
                conn.setWaypoint(wpIdx, wp[0] + dx, wp[1] + dy);
            }
        }

        private void handleConnectClick(MouseEvent e) {
            Object[] portHit = findPortAt(e.getX(), e.getY());
            if (!connecting) {
                if (portHit != null) {
                    connecting = true;
                    connectionSource = (Block) portHit[0];
                    connectionSourceSide = (Block.PortSide) portHit[1];
                    pendingWaypoints.clear();
                    tempLineX = e.getX(); tempLineY = e.getY();
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
                        tempLineX = e.getX(); tempLineY = e.getY();
                        selectedBlock = hit;
                        selectedConnection = null;
                        repaint();
                    }
                }
                return;
            }
            if (portHit != null) {
                Block target = (Block) portHit[0];
                if (target != connectionSource) {
                    finishConnection(target, (Block.PortSide) portHit[1]);
                }
                return;
            }
            Block hit = findBlockAt(e.getX(), e.getY());
            if (hit != null && hit != connectionSource) {
                finishConnection(hit, hit.bestSideToward(connectionSource.centerX(), connectionSource.centerY()));
                return;
            }
            pendingWaypoints.add(new int[]{e.getX(), e.getY()});
            tempLineX = e.getX(); tempLineY = e.getY();
            repaint();
        }

        private void finishConnection(Block target, Block.PortSide targetSide) {
            boolean exists = diagram.getConnections().stream()
                    .anyMatch(c -> c.getFrom() == connectionSource && c.getTo() == target);
            if (!exists) {
                diagram.addConnection(new Connection(
                        java.util.UUID.randomUUID().toString(),
                        connectionSource, target, currentStyle,
                        connectionSourceSide, targetSide, pendingWaypoints));
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
                    if (Math.hypot(x - p[0], y - p[1]) <= PORT_HIT) return new Object[]{b, side};
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
                if (d < bestDist) { bestDist = d; best = side; }
            }
            return best;
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

        /** @return int[]{segIndex, ctrlWhich} oder null */
        private int[] hitBezierControl(int px, int py, Connection conn) {
            conn.ensureSegmentCount();
            List<int[]> pts = routePoints(conn);
            for (int i = 0; i < conn.getSegments().size(); i++) {
                SegmentData seg = conn.getSegment(i);
                if (seg == null || seg.getKind() != SegmentData.Kind.BEZIER) continue;
                int[] ctrls = resolveControls(seg, pts.get(i), pts.get(i + 1));
                if (Math.hypot(px - ctrls[0], py - ctrls[1]) <= CTRL_HIT) return new int[]{i, 1};
                if (Math.hypot(px - ctrls[2], py - ctrls[3]) <= CTRL_HIT) return new int[]{i, 2};
            }
            return null;
        }

        private int[] resolveControls(SegmentData seg, int[] a, int[] b) {
            if (seg.hasControls()) {
                return new int[]{seg.getC1x(), seg.getC1y(), seg.getC2x(), seg.getC2y()};
            }
            return autoBezierControls(a[0], a[1], b[0], b[1]);
        }

        private int hitSegment(int px, int py, Connection conn) {
            List<int[]> pts = routePoints(conn);
            conn.ensureSegmentCount();
            for (int i = 0; i < pts.size() - 1; i++) {
                int[] a = pts.get(i);
                int[] b = pts.get(i + 1);
                SegmentData seg = conn.getSegment(i);
                if (seg != null && seg.getKind() == SegmentData.Kind.BEZIER) {
                    int[] c = resolveControls(seg, a, b);
                    if (distanceToCubic(a[0], a[1], c[0], c[1], c[2], c[3], b[0], b[1], px, py) <= HIT_TOLERANCE)
                        return i;
                } else if (distanceToSegment(a[0], a[1], b[0], b[1], px, py) <= HIT_TOLERANCE) {
                    return i;
                }
            }
            return -1;
        }

        private double distanceToCubic(int x1, int y1, int cx1, int cy1, int cx2, int cy2,
                                       int x2, int y2, int px, int py) {
            double min = Double.MAX_VALUE;
            double prevX = x1, prevY = y1;
            for (int i = 1; i <= 24; i++) {
                double t = i / 24.0;
                double xt = bezierPoint(x1, cx1, cx2, x2, t);
                double yt = bezierPoint(y1, cy1, cy2, y2, t);
                min = Math.min(min, distanceToSegment((int) prevX, (int) prevY, (int) xt, (int) yt, px, py));
                prevX = xt; prevY = yt;
            }
            return min;
        }

        private double bezierPoint(double p0, double p1, double p2, double p3, double t) {
            double u = 1 - t;
            return u*u*u*p0 + 3*u*u*t*p1 + 3*u*t*t*p2 + t*t*t*p3;
        }

        private void handlePopup(MouseEvent e) {
            contextX = e.getX();
            contextY = e.getY();
            Connection hitConn = findConnectionAt(e.getX(), e.getY());
            if (hitConn != null) {
                selectedConnection = hitConn;
                selectedBlock = null;
                styleCombo.setSelectedItem(hitConn.getStyle());
                hitConn.ensureSegmentCount();

                contextWaypointIndex = hitWaypointHandle(e.getX(), e.getY(), hitConn);
                contextSegmentIndex = hitSegment(e.getX(), e.getY(), hitConn);

                deleteHandleItem.setEnabled(contextWaypointIndex >= 0);
                boolean hasSeg = contextSegmentIndex >= 0;
                segAsLineItem.setEnabled(hasSeg);
                segAsBezierItem.setEnabled(hasSeg);

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
                if (hitSegment(px, py, diagram.getConnections().get(i)) >= 0)
                    return diagram.getConnections().get(i);
            }
            return null;
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
                if (selected && draggingEndpoint) drawConnectionDraggingEndpoint(g2, conn);
                else drawConnection(g2, conn, selected);
            }

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
                    int[] a = pts.get(i); int[] b = pts.get(i + 1);
                    g2.drawLine(a[0], a[1], b[0], b[1]);
                }
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
                if (connectMode || isSelected || draggingEndpoint) drawPorts(g2, block);
            }

            if (selectedConnection != null && !draggingEndpoint) {
                drawEndpointHandles(g2, selectedConnection);
                drawWaypointHandles(g2, selectedConnection);
                drawBezierControls(g2, selectedConnection);
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

        private void drawBezierControls(Graphics2D g2, Connection conn) {
            conn.ensureSegmentCount();
            List<int[]> pts = routePoints(conn);
            for (int i = 0; i < conn.getSegments().size(); i++) {
                SegmentData seg = conn.getSegment(i);
                if (seg == null || seg.getKind() != SegmentData.Kind.BEZIER) continue;
                int[] a = pts.get(i);
                int[] b = pts.get(i + 1);
                int[] c = resolveControls(seg, a, b);

                // Hilfslinien Anker → Kontrollpunkt
                g2.setColor(new Color(180, 120, 40, 160));
                g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0,
                        new float[]{4, 4}, 0));
                g2.drawLine(a[0], a[1], c[0], c[1]);
                g2.drawLine(b[0], b[1], c[2], c[3]);

                // Kontrollpunkt-Handles (Rauten)
                drawCtrlHandle(g2, c[0], c[1]);
                drawCtrlHandle(g2, c[2], c[3]);
            }
        }

        private void drawCtrlHandle(Graphics2D g2, int x, int y) {
            int s = 5;
            Path2D diamond = new Path2D.Double();
            diamond.moveTo(x, y - s);
            diamond.lineTo(x + s, y);
            diamond.lineTo(x, y + s);
            diamond.lineTo(x - s, y);
            diamond.closePath();
            g2.setColor(Color.WHITE);
            g2.fill(diamond);
            g2.setColor(new Color(200, 120, 20));
            g2.setStroke(new BasicStroke(1.5f));
            g2.draw(diamond);
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
            conn.ensureSegmentCount();
            g2.setColor(selected ? new Color(200, 80, 40) : new Color(60, 60, 60));
            g2.setStroke(new BasicStroke(selected ? 3.2f : 2.2f));

            for (int i = 0; i < pts.size() - 1; i++) {
                int[] a = pts.get(i);
                int[] b = pts.get(i + 1);
                SegmentData seg = conn.getSegment(i);
                boolean bezier = seg != null && seg.getKind() == SegmentData.Kind.BEZIER;
                // Globaler BEZIER-Style ohne Waypoints → ganzes Stück als Bezier
                if (!bezier && conn.getStyle() == Connection.Style.BEZIER
                        && conn.getWaypoints().isEmpty() && i == 0) {
                    bezier = true;
                }
                if (bezier) {
                    int[] c = (seg != null && seg.getKind() == SegmentData.Kind.BEZIER)
                            ? resolveControls(seg, a, b)
                            : autoBezierControls(a[0], a[1], b[0], b[1]);
                    g2.draw(new CubicCurve2D.Double(a[0], a[1], c[0], c[1], c[2], c[3], b[0], b[1]));
                } else {
                    g2.drawLine(a[0], a[1], b[0], b[1]);
                }
            }
            int[] lastA = pts.get(pts.size() - 2);
            int[] lastB = pts.get(pts.size() - 1);
            drawArrowHead(g2, lastA[0], lastA[1], lastB[0], lastB[1]);
        }

        private void drawConnectionDraggingEndpoint(Graphics2D g2, Connection conn) {
            g2.setColor(new Color(200, 80, 40, 200));
            g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0,
                    new float[]{6, 5}, 0));
            int x1, y1, x2, y2;
            if (draggingWhich == Endpoint.FROM) {
                x1 = endpointDragX; y1 = endpointDragY;
                int[] to = endPoint(conn); x2 = to[0]; y2 = to[1];
            } else {
                int[] from = startPoint(conn); x1 = from[0]; y1 = from[1];
                x2 = endpointDragX; y2 = endpointDragY;
            }
            g2.drawLine(x1, y1, x2, y2);
        }

        private void drawArrowHead(Graphics2D g2, int x1, int y1, int x2, int y2) {
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
