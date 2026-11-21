// src/main/java/hdledit/model/Connection.java
package hdledit.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Connection {

    public enum Style {
        DIRECT,
        ORTHOGONAL,
        BEZIER
    }

    private final String id;
    private Block from;
    private Block to;
    private Port fromPort;
    private Port toPort;
    private Style style;
    private final List<int[]> waypoints = new ArrayList<>();
    private final List<SegmentData> segments = new ArrayList<>();

    public Connection(Block from, Port fromPort, Block to, Port toPort, Style style) {
        this(UUID.randomUUID().toString(), from, fromPort, to, toPort, style, null, null);
    }

    public Connection(String id, Block from, Port fromPort, Block to, Port toPort, Style style,
                      List<int[]> waypoints, List<SegmentData> segments) {
        this.id = id;
        this.from = from;
        this.fromPort = fromPort;
        this.to = to;
        this.toPort = toPort;
        this.style = style != null ? style : Style.DIRECT;
        if (waypoints != null) {
            for (int[] wp : waypoints) this.waypoints.add(new int[]{wp[0], wp[1]});
        }
        if (segments != null) this.segments.addAll(segments);
        ensureSegmentCount();
    }

    public String getId() { return id; }
    public Block getFrom() { return from; }
    public void setFrom(Block from) { this.from = from; }
    public Block getTo() { return to; }
    public void setTo(Block to) { this.to = to; }

    public Port getFromPort() { return fromPort; }
    public void setFromPort(Port fromPort) { this.fromPort = fromPort; }
    public Port getToPort() { return toPort; }
    public void setToPort(Port toPort) { this.toPort = toPort; }

    public Style getStyle() { return style; }
    public void setStyle(Style style) { this.style = style; }

    public List<int[]> getWaypoints() { return waypoints; }
    public List<SegmentData> getSegments() { return segments; }

    public void clearWaypoints() {
        waypoints.clear();
        segments.clear();
        ensureSegmentCount();
    }

    public void addWaypoint(int x, int y) {
        waypoints.add(new int[]{x, y});
        ensureSegmentCount();
    }

    public void insertWaypoint(int index, int x, int y) {
        if (index < 0) index = 0;
        if (index > waypoints.size()) index = waypoints.size();
        waypoints.add(index, new int[]{x, y});
        if (index < segments.size()) {
            segments.add(index, new SegmentData(SegmentData.Kind.LINE));
        }
        ensureSegmentCount();
    }

    public void setWaypoint(int index, int x, int y) {
        if (index >= 0 && index < waypoints.size()) {
            waypoints.get(index)[0] = x;
            waypoints.get(index)[1] = y;
        }
    }

    public void removeWaypoint(int index) {
        if (index >= 0 && index < waypoints.size()) {
            waypoints.remove(index);
            if (index < segments.size()) segments.remove(index);
            ensureSegmentCount();
        }
    }

    public void ensureSegmentCount() {
        int needed = waypoints.size() + 1;
        while (segments.size() < needed) {
            SegmentData.Kind def = (style == Style.BEZIER)
                    ? SegmentData.Kind.BEZIER : SegmentData.Kind.LINE;
            segments.add(new SegmentData(def));
        }
        while (segments.size() > needed) segments.remove(segments.size() - 1);
    }

    public SegmentData getSegment(int index) {
        ensureSegmentCount();
        if (index < 0 || index >= segments.size()) return null;
        return segments.get(index);
    }

    public void setSegmentKind(int index, SegmentData.Kind kind) {
        ensureSegmentCount();
        if (index < 0 || index >= segments.size()) return;
        SegmentData seg = segments.get(index);
        seg.setKind(kind);
        if (kind == SegmentData.Kind.LINE) seg.clearControls();
    }
}
