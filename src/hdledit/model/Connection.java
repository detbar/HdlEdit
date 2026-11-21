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
    private Block.PortSide fromSide;
    private Block.PortSide toSide;
    private Style style;
    /** Zwischenpunkte (nur relevant für ORTHOGONAL). */
    private final List<int[]> waypoints = new ArrayList<>();

    public Connection(Block from, Block to, Style style) {
        this(UUID.randomUUID().toString(), from, to, style, null, null, null);
    }

    public Connection(String id, Block from, Block to, Style style) {
        this(id, from, to, style, null, null, null);
    }

    public Connection(String id, Block from, Block to, Style style,
                      Block.PortSide fromSide, Block.PortSide toSide,
                      List<int[]> waypoints) {
        this.id = id;
        this.from = from;
        this.to = to;
        this.style = style != null ? style : Style.DIRECT;
        this.fromSide = fromSide;
        this.toSide = toSide;
        if (waypoints != null) {
            for (int[] wp : waypoints) {
                this.waypoints.add(new int[]{wp[0], wp[1]});
            }
        }
    }

    public String getId() { return id; }
    public Block getFrom() { return from; }
    public void setFrom(Block from) { this.from = from; }
    public Block getTo() { return to; }
    public void setTo(Block to) { this.to = to; }
    public Style getStyle() { return style; }
    public void setStyle(Style style) { this.style = style; }

    public Block.PortSide getFromSide() { return fromSide; }
    public void setFromSide(Block.PortSide fromSide) { this.fromSide = fromSide; }
    public Block.PortSide getToSide() { return toSide; }
    public void setToSide(Block.PortSide toSide) { this.toSide = toSide; }

    public List<int[]> getWaypoints() { return waypoints; }

    public void clearWaypoints() { waypoints.clear(); }

    public void addWaypoint(int x, int y) {
        waypoints.add(new int[]{x, y});
    }

    public void setWaypoint(int index, int x, int y) {
        if (index >= 0 && index < waypoints.size()) {
            waypoints.get(index)[0] = x;
            waypoints.get(index)[1] = y;
        }
    }
}
